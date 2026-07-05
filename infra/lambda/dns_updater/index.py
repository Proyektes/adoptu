import os
import time

import boto3

ecs = boto3.client("ecs")
ec2 = boto3.client("ec2")
route53 = boto3.client("route53")

# A task can reach RUNNING and then crash seconds later (e.g. a bad image or
# missing runtime dependency) - if this fires on that first RUNNING event
# unconditionally, it points DNS at a task that's about to disappear, taking
# the site down even though a perfectly good previous task might still be up.
# Waiting here and re-checking the task is still RUNNING before touching DNS
# avoids that: a real crash surfaces as a fast, self-correcting "skipped"
# instead of an outage.
STABILITY_WAIT_SECONDS = 12


def handler(event, context):
    detail = event.get("detail", {})
    if detail.get("lastStatus") != "RUNNING":
        result = {"skipped": f"lastStatus={detail.get('lastStatus')}"}
        print(result)
        return result

    cluster_arn = detail["clusterArn"]
    task_arn = detail["taskArn"]

    tasks = ecs.describe_tasks(cluster=cluster_arn, tasks=[task_arn])["tasks"]
    if not tasks:
        print({"skipped": "task not found (already stopped?)", "task_arn": task_arn})
        return {"skipped": "task not found (already stopped?)"}

    time.sleep(STABILITY_WAIT_SECONDS)
    tasks = ecs.describe_tasks(cluster=cluster_arn, tasks=[task_arn])["tasks"]
    if not tasks or tasks[0].get("lastStatus") != "RUNNING":
        result = {
            "skipped": "task no longer RUNNING after stability wait (crashed?)",
            "task_arn": task_arn,
            "last_status": tasks[0].get("lastStatus") if tasks else "not found",
        }
        print(result)
        return result

    # During a normal rolling deployment, the old task and the new task are
    # BOTH briefly RUNNING at once (that's how a zero-downtime deploy works)
    # - EventBridge fires for both, and nothing guarantees the new task's
    # event is processed last. Without this check, the old (soon-to-be-
    # stopped) task's event can be handled after the new one's and overwrite
    # DNS right back to the task that's about to disappear. A task's
    # startedBy is the ECS deployment ID that launched it, so comparing
    # against the service's current PRIMARY deployment tells us whether this
    # task is actually the one the service wants running, not a
    # being-drained leftover from the previous deployment.
    services = ecs.describe_services(cluster=cluster_arn, services=[os.environ["SERVICE_NAME"]])["services"]
    primary_deployment_id = next(
        (d["id"] for d in services[0].get("deployments", []) if d.get("status") == "PRIMARY"),
        None,
    )
    started_by = tasks[0].get("startedBy")
    if primary_deployment_id and started_by != primary_deployment_id:
        result = {
            "skipped": "task belongs to a non-PRIMARY deployment (being drained)",
            "task_arn": task_arn,
            "started_by": started_by,
            "primary_deployment_id": primary_deployment_id,
        }
        print(result)
        return result

    eni_id = next(
        (
            d["value"]
            for attachment in tasks[0].get("attachments", [])
            if attachment.get("type") == "ElasticNetworkInterface"
            for d in attachment.get("details", [])
            if d["name"] == "networkInterfaceId"
        ),
        None,
    )
    if eni_id is None:
        result = {"skipped": "task has no ENI attachment", "task_arn": task_arn}
        print(result)
        return result

    eni = ec2.describe_network_interfaces(NetworkInterfaceIds=[eni_id])["NetworkInterfaces"][0]
    print({"eni_id": eni_id, "eni": eni})
    ipv6_addresses = eni.get("Ipv6Addresses", [])
    if not ipv6_addresses:
        result = {"skipped": "ENI has no IPv6 address yet", "eni_id": eni_id}
        print(result)
        return result
    ipv6 = ipv6_addresses[0]["Ipv6Address"]

    # CloudFront can't resolve a pure IPv6 (AAAA-only) custom origin - it
    # falls back to an A record and connects over IPv4 - so this needs to
    # stay current too, not just the AAAA record.
    public_ipv4 = eni.get("Association", {}).get("PublicIp")

    record_name = os.environ["RECORD_NAME"]
    changes = [
        {
            "Action": "UPSERT",
            "ResourceRecordSet": {
                "Name": record_name,
                "Type": "AAAA",
                "TTL": 60,
                "ResourceRecords": [{"Value": ipv6}],
            },
        }
    ]
    if public_ipv4:
        changes.append(
            {
                "Action": "UPSERT",
                "ResourceRecordSet": {
                    "Name": record_name,
                    "Type": "A",
                    "TTL": 60,
                    "ResourceRecords": [{"Value": public_ipv4}],
                },
            }
        )

    route53.change_resource_record_sets(
        HostedZoneId=os.environ["HOSTED_ZONE_ID"],
        ChangeBatch={"Changes": changes},
    )
    result = {"updated": record_name, "ipv6": ipv6, "ipv4": public_ipv4}
    print(result)
    return result
