#!/usr/bin/env python3
"""Idempotently add correlationId and amount to existing order-state items."""

import argparse
import json
import subprocess
import sys
from decimal import Decimal, InvalidOperation


def aws_json(arguments):
    result = subprocess.run(
        ["aws", "dynamodb", *arguments, "--output", "json"],
        check=True, capture_output=True, text=True,
    )
    return json.loads(result.stdout or "{}")


def scan_items(table_name, aws_call=aws_json):
    items = []
    start_key = None
    while True:
        args = [
            "scan", "--table-name", table_name, "--limit", "100", "--no-paginate",
            "--projection-expression", "#oid, #cid, #amt",
            "--expression-attribute-names",
            json.dumps({"#oid": "orderId", "#cid": "correlationId", "#amt": "amount"}),
        ]
        if start_key:
            args.extend(["--exclusive-start-key", json.dumps(start_key, separators=(",", ":"))])
        response = aws_call(args)
        items.extend(response.get("Items", []))
        start_key = response.get("LastEvaluatedKey")
        if not start_key:
            return items


def normalize_mapping(mapping, order):
    entry = mapping.get(order)
    if not isinstance(entry, dict):
        raise ValueError(f"Mapeamento ausente para pedido {order}")
    correlation_id = entry.get("correlationId")
    amount = entry.get("amount")
    if not isinstance(correlation_id, str) or not correlation_id.strip():
        raise ValueError(f"correlationId inválido no mapeamento do pedido {order}")
    try:
        amount_decimal = Decimal(str(amount))
    except (InvalidOperation, TypeError, ValueError) as error:
        raise ValueError(f"amount inválido no mapeamento do pedido {order}") from error
    if not amount_decimal.is_finite():
        raise ValueError(f"amount inválido no mapeamento do pedido {order}")
    amount_string = str(amount) if isinstance(amount, str) else format(amount_decimal, "f")
    return correlation_id, amount_string


def same_amount(left, right):
    try:
        return Decimal(left) == Decimal(right)
    except (InvalidOperation, TypeError, ValueError):
        return False


def migrate(table_name, mapping, aws_call=aws_json):
    # Read and validate the entire table before the first write.
    updates = []
    for item in scan_items(table_name, aws_call):
        order = item.get("orderId", {}).get("S")
        if not order:
            raise ValueError("Item sem orderId encontrado na tabela")
        existing_correlation = item.get("correlationId", {}).get("S")
        existing_amount = item.get("amount", {}).get("S")
        if existing_correlation and existing_amount:
            continue
        correlation_id, amount = normalize_mapping(mapping, order)
        if existing_correlation and existing_correlation != correlation_id:
            raise ValueError(f"correlationId do mapeamento diverge do DynamoDB para pedido {order}")
        if existing_amount and not same_amount(existing_amount, amount):
            raise ValueError(f"amount do mapeamento diverge do DynamoDB para pedido {order}")
        updates.append((order, correlation_id, amount))

    for order, correlation_id, amount in updates:
        args = [
            "update-item", "--table-name", table_name,
            "--key", json.dumps({"orderId": {"S": order}}, separators=(",", ":")),
            "--update-expression", "SET #cid = if_not_exists(#cid, :cid), #amt = if_not_exists(#amt, :amt)",
            "--expression-attribute-names", json.dumps({"#cid": "correlationId", "#amt": "amount"}),
            "--expression-attribute-values", json.dumps({":cid": {"S": correlation_id}, ":amt": {"S": amount}}),
        ]
        aws_call(args)
    return len(updates)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--table-name", required=True)
    parser.add_argument("--mapping-file", required=True, help="JSON map: orderId -> {correlationId, amount}")
    args = parser.parse_args(argv)
    try:
        with open(args.mapping_file, encoding="utf-8") as mapping_file:
            mapping = json.load(mapping_file, parse_float=Decimal)
        if not isinstance(mapping, dict):
            raise ValueError("O arquivo de mapeamento deve conter um objeto JSON")
        count = migrate(args.table_name, mapping)
        print(f"Migração concluída: {count} item(ns) atualizado(s) em {args.table_name}.")
        return 0
    except (OSError, json.JSONDecodeError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Falha na migração de order-state: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
