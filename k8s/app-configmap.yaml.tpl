apiVersion: v1
kind: ConfigMap
metadata:
  name: os-orchestrator-config
  namespace: os-orchestrator
data:
  SERVER_PORT: "8080"
  AWS_REGION: "${aws_region}"
  # Queue and table names are deterministic (os-<service>-<env>-<purpose>), so the
  # deploy pipeline builds them from the branch name instead of reading another
  # repo's Terraform state.
  ORDER_PROCESSING_QUEUE: "${order_processing_queue}"
  ORDER_STATE_TABLE: "${order_state_table}"
  DD_ENV: "${datadog_env}"
  DD_SERVICE: "os-orchestrator"
  DD_VERSION: "1.0.0"
