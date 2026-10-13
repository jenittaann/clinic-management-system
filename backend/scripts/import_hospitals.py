#!/usr/bin/env python3
"""Validate and safely import Chennai hospital rows into the Camillus database.

Requires mysql-connector-python and DB_URL/DB_USERNAME/DB_PASSWORD (or the
equivalent DB_HOST/DB_PORT/DB_NAME values). The importer only inserts rows.
"""

import argparse
import csv
import os
import re
import sys
import uuid
from dataclasses import dataclass
from urllib.parse import parse_qs, unquote

COLUMNS = (
    "public_id", "name", "type", "area", "address", "beds", "description",
    "source_name", "source_url", "source_record_id",
)
VARCHAR_LIMITS = {
    "public_id": 36,
    "name": 255,
    "type": 100,
    "area": 160,
    "address": 500,
    "source_name": 255,
    "source_url": 1000,
    "source_record_id": 255,
}
MYSQL_INT_MAX = 2_147_483_647
TEXT_MAX_BYTES = 65_535

INSERT_SQL = """INSERT INTO hospitals
    (public_id, name, type, area, address, beds, description,
     source_name, source_url, source_record_id)
    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)"""


@dataclass
class CsvRow:
    line_number: int
    values: tuple


def database_config():
    """Resolve connection environment variables and enforce the safe database."""
    required_settings = ("DB_URL", "DB_USERNAME", "DB_PASSWORD")
    missing = [name for name in required_settings if not os.getenv(name, "").strip()]
    if missing:
        raise ValueError("Required database settings are missing or blank: " + ", ".join(missing))

    url = os.environ["DB_URL"]
    match = re.fullmatch(r"jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)(?:\?(.*))?", url)
    if not match:
        raise ValueError("DB_URL must be a jdbc:mysql://host:port/database URL")

    host, port, url_database, query = match.groups()
    url_database = unquote(url_database)
    override_database = os.getenv("DB_NAME")
    database = override_database if override_database is not None else url_database

    # Reject conflicting settings too, even when DB_NAME would otherwise win.
    if url_database != "camillus" or database != "camillus":
        raise ValueError(
            "Refusing to connect: both the database in DB_URL and the resolved "
            "database must be exactly 'camillus'."
        )

    params = parse_qs(query or "")
    resolved_host = os.getenv("DB_HOST", host)
    if not resolved_host.strip():
        raise ValueError("DB_HOST must not be blank")

    return {
        "host": resolved_host,
        "port": int(os.getenv("DB_PORT", port or 3306)),
        "database": database,
        "user": os.environ["DB_USERNAME"],
        "password": os.environ["DB_PASSWORD"],
        "connection_timeout": 5,
        "charset": "utf8mb4",
        "use_unicode": True,
        # A numeric offset avoids relying on MySQL's named timezone tables.
        "time_zone": params.get("serverTimezone", ["+00:00"])[0]
        if params.get("serverTimezone", ["+00:00"])[0] != "UTC"
        else "+00:00",
    }


def blank_to_none(value):
    return None if value is None or value.strip() == "" else value


def source_key(source_name, source_record_id):
    if not source_name or not source_record_id:
        return None
    # MySQL's usual case-insensitive collations treat these as the same key.
    return source_name.strip().casefold(), source_record_id.strip().casefold()


def validate_csv(path):
    """Read and validate every CSV row before any database connection is made."""
    valid_rows = []
    invalid_rows = []
    seen_public_ids = set()
    seen_source_keys = set()
    duplicate_public_ids = 0
    duplicate_source_keys = 0

    with open(path, "r", newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        if reader.fieldnames is None:
            raise ValueError("CSV is empty or has no header row")
        if tuple(reader.fieldnames) != COLUMNS:
            raise ValueError(
                "CSV headers must exactly match this ordered list: "
                + ", ".join(COLUMNS)
            )

        for line_number, row in enumerate(reader, start=2):
            problems = []
            if None in row:
                problems.append("row has more values than header columns")
            if any(row.get(column) is None for column in COLUMNS):
                problems.append("row has fewer values than header columns")
            if problems:
                invalid_rows.append((line_number, "; ".join(problems)))
                continue

            normalized = {column: blank_to_none(row[column]) for column in COLUMNS}
            if normalized["name"] is None:
                problems.append("name is required")

            for column, limit in VARCHAR_LIMITS.items():
                value = normalized[column]
                if value is not None and len(value) > limit:
                    problems.append(
                        f"{column} is {len(value)} characters; maximum is {limit}"
                    )

            description = normalized["description"]
            if description is not None:
                try:
                    description_bytes = len(description.encode("utf-8"))
                except UnicodeEncodeError:
                    problems.append("description cannot be encoded as UTF-8")
                else:
                    if description_bytes > TEXT_MAX_BYTES:
                        problems.append(
                            f"description is {description_bytes} UTF-8 bytes; "
                            f"MySQL TEXT maximum is {TEXT_MAX_BYTES} bytes"
                        )

            raw_beds = normalized["beds"].strip() if normalized["beds"] is not None else None
            beds = None
            if raw_beds is not None:
                if not re.fullmatch(r"\d+", raw_beds):
                    problems.append("beds must be a non-negative whole number")
                else:
                    significant_beds = raw_beds.lstrip("0") or "0"
                    max_beds = str(MYSQL_INT_MAX)
                    if len(significant_beds) > len(max_beds) or (
                        len(significant_beds) == len(max_beds)
                        and significant_beds > max_beds
                    ):
                        problems.append(
                            f"beds exceeds MySQL INT maximum {MYSQL_INT_MAX}"
                        )
                    elif significant_beds != "0":
                        beds = int(significant_beds)
                    # Blank and zero bed counts both map to SQL NULL.

            public_id = normalized["public_id"].strip() if normalized["public_id"] is not None else None
            if public_id is None:
                public_id = str(uuid.uuid4())
            else:
                try:
                    public_id = str(uuid.UUID(public_id))
                except (ValueError, AttributeError):
                    problems.append("public_id must be a valid UUID or blank")

            if problems:
                invalid_rows.append((line_number, "; ".join(problems)))
                continue

            duplicate_key = source_key(
                normalized["source_name"], normalized["source_record_id"]
            )

            if public_id.lower() in seen_public_ids:
                duplicate_public_ids += 1
                continue
            if duplicate_key is not None and duplicate_key in seen_source_keys:
                duplicate_source_keys += 1
                continue

            seen_public_ids.add(public_id.lower())
            if duplicate_key is not None:
                seen_source_keys.add(duplicate_key)

            values = (
                public_id,
                normalized["name"],
                normalized["type"],
                normalized["area"],
                normalized["address"],
                beds,
                description,
                normalized["source_name"],
                normalized["source_url"],
                normalized["source_record_id"],
            )
            valid_rows.append(CsvRow(line_number, values))

    return valid_rows, invalid_rows, duplicate_public_ids, duplicate_source_keys


def report_preflight(valid_rows, invalid_rows, duplicate_public_ids, duplicate_source_keys):
    for line_number, problem in invalid_rows:
        print(f"CSV row {line_number}: INVALID: {problem}", file=sys.stderr)
    print(
        "Preflight summary: "
        f"valid={len(valid_rows)}, invalid={len(invalid_rows)}, "
        f"duplicate_public_id={duplicate_public_ids}, "
        f"duplicate_source_key={duplicate_source_keys}"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv_file", help="Path to camillus_chennai_hospitals.csv")
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate the entire CSV without connecting to MySQL",
    )
    args = parser.parse_args()

    try:
        valid_rows, invalid_rows, duplicate_public_ids, duplicate_source_keys = validate_csv(
            args.csv_file
        )
    except (OSError, UnicodeError, csv.Error, ValueError) as error:
        print(f"CSV preflight failed: {error}", file=sys.stderr)
        return 2

    report_preflight(
        valid_rows, invalid_rows, duplicate_public_ids, duplicate_source_keys
    )
    if invalid_rows:
        print("Import aborted: fix all invalid CSV rows before importing.", file=sys.stderr)
        return 1
    if args.validate_only:
        print("Validation passed; no database connection was made.")
        return 0

    try:
        import mysql.connector
    except ImportError:
        print(
            "Missing dependency mysql-connector-python; install it with: "
            "python -m pip install mysql-connector-python",
            file=sys.stderr,
        )
        return 2

    try:
        config = database_config()
    except (TypeError, ValueError) as error:
        print(f"Database target rejected: {error}", file=sys.stderr)
        return 2

    try:
        connection = mysql.connector.connect(**config)
    except Exception as error:
        print(
            f"Database connection failed ({type(error).__name__}); no rows imported.",
            file=sys.stderr,
        )
        return 2

    cursor = None
    inserted = skipped_public_id = skipped_source_key = 0
    try:
        cursor = connection.cursor()
        cursor.execute(
            "SELECT ENGINE FROM information_schema.tables "
            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'hospitals'"
        )
        engine_row = cursor.fetchone()
        if not engine_row or (engine_row[0] or "").upper() != "INNODB":
            print(
                "Import aborted: camillus.hospitals is missing or does not use "
                "the InnoDB transactional engine; no rows were inserted.",
                file=sys.stderr,
            )
            return 1

        cursor.execute("SELECT public_id, source_name, source_record_id FROM hospitals")
        existing_rows = cursor.fetchall()
        existing_ids = {
            public_id.lower()
            for public_id, _source_name, _source_record_id in existing_rows
            if public_id
        }
        existing_source_keys = {
            source_key(source_name, source_record_id)
            for _public_id, source_name, source_record_id in existing_rows
            if source_name and source_record_id
        }

        # End the read-only SELECT transaction before beginning the insert transaction.
        connection.commit()
        # All inserts are enclosed in one transaction.
        connection.start_transaction()
        for row in valid_rows:
            public_id, *rest = row.values
            duplicate_key = source_key(rest[6], rest[8])
            if public_id.lower() in existing_ids:
                skipped_public_id += 1
                continue
            if duplicate_key is not None and duplicate_key in existing_source_keys:
                skipped_source_key += 1
                continue

            try:
                cursor.execute(INSERT_SQL, row.values)
            except Exception as error:
                connection.rollback()
                print(
                    f"Database insertion failed at CSV row {row.line_number} "
                    f"({type(error).__name__}); transaction rolled back, "
                    "no rows from this run were committed.",
                    file=sys.stderr,
                )
                return 1

            inserted += 1
            existing_ids.add(public_id.lower())
            if duplicate_key is not None:
                existing_source_keys.add(duplicate_key)

        connection.commit()
        print(
            "Import complete: "
            f"inserted={inserted}, "
            f"skipped_existing_public_id={skipped_public_id}, "
            f"skipped_existing_source_key={skipped_source_key}, "
            f"invalid={len(invalid_rows)}, "
            f"duplicate_public_id={duplicate_public_ids}, "
            f"duplicate_source_key={duplicate_source_keys}"
        )
        return 0
    except Exception as error:
        connection.rollback()
        print(
            f"Import failed ({type(error).__name__}); transaction rolled back.",
            file=sys.stderr,
        )
        return 1
    finally:
        if cursor is not None:
            cursor.close()
        connection.close()


if __name__ == "__main__":
    raise SystemExit(main())
