#!/bin/bash
# Fan-out: SNS transaction-events -> SQS fraud-queue (antifraude). Notificação e analytics
# assinariam o mesmo tópico com suas próprias filas.
set -e
awslocal sqs create-queue --queue-name fraud-dlq
DLQ_ARN=$(awslocal sqs get-queue-attributes --queue-url http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/fraud-dlq \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
awslocal sqs create-queue --queue-name fraud-queue \
  --attributes "{\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"$DLQ_ARN\\\",\\\"maxReceiveCount\\\":\\\"3\\\"}\"}"
awslocal sns create-topic --name transaction-events
awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:transaction-events \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:fraud-queue \
  --attributes RawMessageDelivery=true
