import unittest

from migrate_order_state import migrate


def attr(value):
    return {"S": value}


class MigrationTests(unittest.TestCase):
    def test_scans_multiple_pages_and_updates_idempotently(self):
        calls = []

        def aws(args):
            calls.append(args)
            if args[0] == "scan":
                if "--exclusive-start-key" not in args:
                    return {"Items": [{"orderId": attr("o1")}], "LastEvaluatedKey": {"orderId": attr("o1")}}
                return {"Items": [{"orderId": attr("o2")}]}
            return {}

        mapping = {"o1": {"correlationId": "c1", "amount": "50.30"},
                   "o2": {"correlationId": "c2", "amount": "12.00"}}
        self.assertEqual(2, migrate("orders", mapping, aws))
        writes = [call for call in calls if call[0] == "update-item"]
        self.assertEqual(2, len(writes))
        expression = writes[0][writes[0].index("--update-expression") + 1]
        self.assertIn("if_not_exists", expression)

    def test_missing_mapping_aborts_before_any_update(self):
        calls = []

        def aws(args):
            calls.append(args)
            return {"Items": [{"orderId": attr("legacy")}]} if args[0] == "scan" else {}

        with self.assertRaisesRegex(ValueError, "Mapeamento ausente"):
            migrate("orders", {}, aws)
        self.assertFalse(any(call[0] == "update-item" for call in calls))

    def test_empty_table_needs_no_mapping(self):
        calls = []

        def aws(args):
            calls.append(args)
            return {"Items": []}

        self.assertEqual(0, migrate("orders", {}, aws))
        self.assertEqual(1, len(calls))

    def test_already_migrated_items_are_skipped(self):
        calls = []

        def aws(args):
            calls.append(args)
            return {"Items": [{"orderId": attr("done"), "correlationId": attr("c"), "amount": attr("50.30")}]}

        self.assertEqual(0, migrate("orders", {}, aws))
        self.assertFalse(any(call[0] == "update-item" for call in calls))


if __name__ == "__main__":
    unittest.main()
