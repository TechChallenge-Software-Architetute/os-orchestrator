# os-orchestrator owns exactly two pieces of infrastructure: the queue it consumes
# from and the table it keeps saga state in. It never references another repo's
# Terraform state, and never creates a resource inside another repo's stack — so
# this deploy can run at any time, in any order, on an empty account.
#
# Foreign queues (os-payment's request queue, os-management's event queue) are
# addressed at runtime by deterministic name: os-<service>-<env>-<purpose>.

locals {
  queue_name  = "os-orchestrator-${var.environment}-order-processing"
  dlq_name    = "os-orchestrator-${var.environment}-order-processing-dlq"
  state_table = var.order_state_table_name != "" ? var.order_state_table_name : "os-orchestrator-${var.environment}-order-state"
}

# --- Inbound queue + DLQ -----------------------------------------------------

resource "aws_sqs_queue" "order_processing_dlq" {
  name                      = local.dlq_name
  message_retention_seconds = var.dlq_message_retention_seconds

  # SSE with the SQS-managed key: encryption at rest without an extra KMS grant
  # for every producer and consumer.
  sqs_managed_sse_enabled = true
}

resource "aws_sqs_queue" "order_processing" {
  name                       = local.queue_name
  visibility_timeout_seconds = var.queue_visibility_timeout_seconds
  message_retention_seconds  = var.queue_message_retention_seconds
  sqs_managed_sse_enabled    = true

  # Long polling: fewer empty receives, and it matches the listener's poll-timeout.
  receive_wait_time_seconds = 20

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.order_processing_dlq.arn
    maxReceiveCount     = var.queue_max_receive_count
  })
}

# Resource policy only for cross-account producers. Same-account producers
# (os-management on EKS, os-payment's Lambda) are authorised by their own IAM
# policies, so the default of an empty list creates nothing here.
data "aws_iam_policy_document" "order_processing" {
  count = length(var.producer_role_arns) > 0 ? 1 : 0

  statement {
    sid       = "AllowNamedProducersToSend"
    effect    = "Allow"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.order_processing.arn]

    principals {
      type        = "AWS"
      identifiers = var.producer_role_arns
    }
  }

  # Belt and braces: reject anything that reaches the queue over plain HTTP.
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["sqs:*"]
    resources = [aws_sqs_queue.order_processing.arn]

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_sqs_queue_policy" "order_processing" {
  count = length(var.producer_role_arns) > 0 ? 1 : 0

  queue_url = aws_sqs_queue.order_processing.id
  policy    = data.aws_iam_policy_document.order_processing[0].json
}

# --- Saga state table --------------------------------------------------------

resource "aws_dynamodb_table" "order_state" {
  name         = local.state_table
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "orderId"

  attribute {
    name = "orderId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = var.enable_point_in_time_recovery
  }

  server_side_encryption {
    enabled = true
  }

  lifecycle {
    # The saga state is the system of record for in-flight orders; a rename or a
    # forced replacement would drop it.
    prevent_destroy = true
  }
}
