#!/bin/bash

set -e

for queue in \
  order-processing-queue \
  os-management-queue \
  os-payment-queue \
  error-queue
do
  awslocal sqs create-queue --queue-name "$queue"
done

awslocal dynamodb create-table \
  --table-name order-state \
  --attribute-definitions \
      AttributeName=orderId,AttributeType=S \
  --key-schema \
      AttributeName=orderId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "SQS queue and DynamoDB table created"