variable "region" {
  description = "AWS region."
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "Expected AWS account ID. Guards the provider against deploying to the wrong account. Empty = no restriction (local validation)."
  type        = string
  default     = ""
}

variable "environment" {
  description = "Environment name. By convention this is the branch name (develop/main)."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,20}$", var.environment))
    error_message = "environment must be lowercase alphanumeric with hyphens, starting with a letter."
  }
}

# --- Inbound queue -----------------------------------------------------------

variable "queue_visibility_timeout_seconds" {
  description = "Visibility timeout for the order-processing queue. Must exceed the listener's worst-case processing time."
  type        = number
  default     = 60
}

variable "queue_message_retention_seconds" {
  description = "How long an unconsumed message stays on the main queue (seconds)."
  type        = number
  default     = 345600 # 4 days
}

variable "dlq_message_retention_seconds" {
  description = "How long a message stays on the DLQ (seconds). Longer than the main queue so failures can be inspected."
  type        = number
  default     = 1209600 # 14 days
}

variable "queue_max_receive_count" {
  description = "Deliveries attempted before a message is redriven to the DLQ."
  type        = number
  default     = 5
}

variable "producer_role_arns" {
  description = <<-EOT
    IAM role/user ARNs allowed to sqs:SendMessage to the order-processing queue —
    the os-management router (saga start) and the os-payment handler (saga result).

    Empty (the default) creates no resource policy at all: same-account producers are
    then authorised by their own IAM policies, which is the paved-path shape and keeps
    this deploy independent of the producer repos. Populate it only for cross-account
    producers. Never widen this to "*".
  EOT
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.producer_role_arns, "*")
    error_message = "producer_role_arns must name specific principals; a wildcard principal would make the queue writable by any AWS account."
  }
}

# --- State table -------------------------------------------------------------

variable "order_state_table_name" {
  description = "DynamoDB table holding the saga state per order. Defaults to os-orchestrator-<env>-order-state."
  type        = string
  default     = ""
}

variable "enable_point_in_time_recovery" {
  description = "Enable DynamoDB point-in-time recovery on the state table."
  type        = bool
  default     = true
}
