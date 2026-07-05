resource "aws_db_instance" "postgres" {
  identifier     = var.db_identifier
  engine         = "postgres"
  engine_version = var.db_engine_version

  instance_class        = var.db_instance_class
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  storage_type          = "gp2"
  storage_encrypted     = true
  kms_key_id            = var.db_kms_key_id

  db_name  = var.db_name
  username = var.db_username
  password = random_password.rds_master.result

  db_subnet_group_name   = data.aws_db_subnet_group.default.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # No longer needed: ECS tasks ended up dual-stack (real private IPv4,
  # not IPv6-only - see network.tf/security_groups.tf for why), so they
  # already reach this IPv4-only instance directly within the VPC. A prior
  # attempt to set this to "DUAL" for a pure-IPv6-only design never actually
  # took effect anyway (AWS accepted the apply but PendingModifiedValues
  # stayed empty and NetworkType stayed IPV4) and would just be a
  # perpetually-drifting no-op now, so leaving it unmanaged/default (IPV4,
  # matching the live instance).

  multi_az            = false
  publicly_accessible = false

  backup_retention_period = 7
  backup_window           = "06:20-06:50"
  maintenance_window      = "wed:04:14-wed:04:44"

  performance_insights_enabled          = true
  performance_insights_retention_period = 7

  monitoring_interval = 60
  monitoring_role_arn = data.aws_iam_role.rds_monitoring.arn

  ca_cert_identifier = "rds-ca-rsa2048-g1"

  copy_tags_to_snapshot = true

  # Live instance currently has this off; defaulting on here since this is a
  # production database. Override db_deletion_protection if you actually
  # intend to destroy/recreate it.
  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.db_identifier}-final"

  apply_immediately = false

  tags = {
    Name = var.db_identifier
  }
}
