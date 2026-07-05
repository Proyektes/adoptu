import os

import boto3

ecs = boto3.client("ecs")
ec2 = boto3.client("ec2")
route53 = boto3.client("route53")


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
