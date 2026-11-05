# Consumed by the deploy job to populate the app's ConfigMap. Producer repos do
# NOT read these via terraform_remote_state — they rebuild the name themselves
# from os-<service>-<env>-<purpose>, which is what keeps the deploys independent.

output "order_processing_queue_name" {
  description = "Name of the inbound order-processing queue."
  value       = aws_sqs_queue.order_processing.name
}

output "order_processing_queue_url" {
  description = "URL of the inbound order-processing queue."
  value       = aws_sqs_queue.order_processing.url
}

output "order_processing_queue_arn" {
  description = "ARN of the inbound order-processing queue. Producers grant sqs:SendMessage on this in their own IAM policy."
  value       = aws_sqs_queue.order_processing.arn
}

output "order_processing_dlq_url" {
  description = "URL of the order-processing dead-letter queue."
  value       = aws_sqs_queue.order_processing_dlq.url
}

output "order_state_table_name" {
  description = "DynamoDB table holding saga state per order."
  value       = aws_dynamodb_table.order_state.name
}

output "order_state_table_arn" {
  description = "ARN of the saga state table."
  value       = aws_dynamodb_table.order_state.arn
}
