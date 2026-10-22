#!/bin/bash

set -e

awslocal sqs create-queue \
  --queue-name order-processing-queue

awslocal dynamodb create-table \
  --table-name order-state \
  --attribute-definitions \
      AttributeName=orderId,AttributeType=S \
  --key-schema \
      AttributeName=orderId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

echo "SQS queue and DynamoDB table created"