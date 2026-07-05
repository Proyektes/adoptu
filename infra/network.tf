# Dual-stack subnets for ECS Fargate tasks. Originally built pure
# IPv6-only (ipv6_native, no IPv4 CIDR at all), on the assumption that the
# live "Adopt-u-ipv6" service is IPv6-only too - it isn't. Its actual task
# runs in subnet-0a9fb3d032658fc10, a dual-stack subnet, with
# assign_public_ip = ENABLED giving it a real public IPv4 (see ecs.tf).
# That IPv4 path is required because ECR's registry API has no IPv6
# support: a pure-IPv6 subnet can start a task but every image pull then
# fails with "network is unreachable". No NAT Gateway needed either way -
# the VPC's main route table already routes both `0.0.0.0/0` and `::/0`
# straight to the Internet Gateway, so a public IPv4 (via
# assign_public_ip) is enough on its own, exactly like the live service.
#
# Deliberately not creating/associating an explicit route table: the
# account's default VPC main route table already has the ::/0 -> igw and
# 0.0.0.0/0 -> igw routes, and any subnet without an explicit association
# inherits it automatically. Managing the main route table here would risk
# touching every other subnet in the default VPC, which is out of scope
# for this stack.

resource "aws_subnet" "ecs_ipv6_only" {
  for_each = { for idx, az in var.ipv6_subnet_azs : az => idx }

  vpc_id            = data.aws_vpc.target.id
  availability_zone = each.key

  # /64 indices 0-7 are all already in use by pre-existing subnets in this
  # VPC - including 6 and 7, which turned out to be the old hand-created
  # "ecs-ipv6-only-subnet(-2)" pair (unused leftovers - the live service
  # actually runs elsewhere, see above). Start fresh ones at 8 to avoid an
  # InvalidSubnet.Conflict and to keep this stack's compute genuinely
  # separate from the old service's.
  ipv6_native     = false
  cidr_block      = cidrsubnet("172.31.96.0/20", 4, each.value) # 172.31.96.0/24, 172.31.97.0/24 - free /20 block, unused by any existing subnet
  ipv6_cidr_block = cidrsubnet(data.aws_vpc.target.ipv6_cidr_block, 8, 8 + each.value)
  enable_dns64    = false

  map_public_ip_on_launch                        = true
  assign_ipv6_address_on_creation                = true
  private_dns_hostname_type_on_launch            = "resource-name"
  enable_resource_name_dns_aaaa_record_on_launch = true

  tags = {
    Name = "adoptu-ecs-ipv6-only-${each.key}"
  }
}
