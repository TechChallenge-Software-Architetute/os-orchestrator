apiVersion: v1
kind: Secret
metadata:
  name: os-orchestrator-secret
  namespace: os-orchestrator
type: Opaque
stringData:
  # The cluster has no IRSA/OIDC provider (os-management-k8s-terraform sets neither),
  # so the pod gets static keys here, substituted by the pipeline from GitHub
  # Secrets. No credential value is ever committed to this file.
  #
  # FOLLOW-UP: enable `enable_irsa = true` on the EKS module and give this
  # deployment a service-account role instead. Static keys in a Secret are
  # long-lived and visible to anything that can read Secrets in this namespace.
  AWS_ACCESS_KEY_ID: "${aws_access_key_id}"
  AWS_SECRET_ACCESS_KEY: "${aws_secret_access_key}"
  AWS_SESSION_TOKEN: "${aws_session_token}"
  DD_API_KEY: "${dd_api_key}"
