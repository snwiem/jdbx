# jdbx demo database

A small PostgreSQL database with a fictional online bookshop, **Pagebound Books**. Use it to try jdbx, or to record a demo.

All data is invented. The data is the same on every start: it has no `random()` and no `now()`.

## Start

You need Podman or Docker. Run this from the root of the repository:

```sh
podman compose -f demo/compose.yaml up -d --build --wait
```

Use `docker compose` instead of `podman compose` if you use Docker. The command builds a small image from `demo/Containerfile` and starts the container `jdbx-demo`. The first start loads the scripts `demo/*.sql`. It takes a few seconds.

Without Compose:

```sh
podman build -t jdbx-demo-postgres:local -f demo/Containerfile demo
podman run -d --name jdbx-demo -p 5433:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo jdbx-demo-postgres:local
```

The scripts are copied into the image. A bind mount is not used, so this also works with a remote container engine.

| Setting | Value |
|---|---|
| Host | `localhost` |
| Port | `5433` (the `compose.yaml` in the project root uses `5432`) |
| Database, user, password | `demo` |
| Default schema | `shop` (also `ref` and `audit` on the search path) |

## Connect

Batch mode:

```sh
jdbx --host localhost --port 5433 --db demo --user demo --password demo -c 'select * from products limit 5'
```

Interactive mode with a saved Profile: start `jdbx`, then use `/profile add` and fill in the dialog with the values above. Name the Profile `demo`. Later, start with `jdbx -p demo`.

## Reset and stop

```sh
podman compose -f demo/compose.yaml down -v   # stop and delete the data
podman compose -f demo/compose.yaml up -d --build --wait   # start again with fresh data
```

## What is in it

| Schema | Table or view | Rows | Notes |
|---|---|---|---|
| `shop` | `customers` | 200 | `uuid` key, `jsonb` preferences, `NULL`s, emoji and umlauts |
| `shop` | `products` | 40 | `text[]` tags, `jsonb` attributes, long `description` |
| `shop` | `orders` | 1,200 | six statuses, `jsonb` address, partial index for open orders |
| `shop` | `order_items` | about 3,000 | composite primary key |
| `shop` | `reviews` | 400 | one review per customer and book |
| `shop` | `v_order_totals`, `v_top_products` | | views with joins and sums |
| `shop` | `v_customer_overview` | 200 | a very wide view (23 columns) |
| `audit` | `events` | 2,500 | more than the default Row limit of 1000 |
| `ref` | `countries`, `currencies` | 24, 15 | reference data |

Tables and views have comments. `/describe <table>` shows them as remarks.

## Demo script

About two minutes. Run it in a terminal of about 110 columns and 30 rows, with a large font.

| Step | Type | What it shows |
|---|---|---|
| 1 | `/describe` | Schemas, tables and views of the database |
| 2 | `/describe products` | Columns, types, defaults, remarks, primary key and indexes |
| 3 | `select  from products;` (put the cursor after `select `, press `Tab`) | Completion of column names |
| 4 | `select title, author, price from products order by price desc limit 5;` | A Result as a table |
| 5 | `/format markdown`, then `/format csv`, then `/format json-pretty`, then run the Statement again (`Up`) | Formats. `Up` takes the Statement from History |
| 6 | `/format table`, then `select * from v_customer_overview limit 2;` | A wide Result that does not fit |
| 7 | `/orientation vertical`, then run it again | Orientation: one field per line |
| 8 | `/format json-pretty`, then `select full_name, preferences from customers limit 3;` | `jsonb` as real JSON |
| 9 | `/copy` | Copy the Result to the clipboard |
| 10 | `Ctrl-R`, then type `audit` | Search History |
| 11 | `/limit 5`, then `select * from audit.events;` | The Row limit, and the truncation hint |
| 12 | `/dump events.json select * from audit.events;` | Dump: all 2,500 rows, no Row limit |
| 13 | `/readonly on`, then `update products set price = 1;` | Read-only: the write is refused |
| 14 | `select pg_sleep(30);`, then `Ctrl-C` | Cancel a running Statement |
| 15 | `/quit`, start `jdbx` again, then `/resume` | Resume an earlier Session |

More Statements for the demo:

```sql
-- Which books earn the most?
select title, units_sold, revenue, avg_rating from v_top_products order by revenue desc limit 10;

-- Orders per status
select status, count(*) from orders group by status order by 2 desc;

-- Customers per country, with the region
select k.region, k.name, count(*) from customers c join countries k on k.code = c.country_code group by 1, 2 order by 3 desc;

-- Search inside jsonb
select full_name from customers where preferences ->> 'theme' = 'dark' and (preferences ->> 'newsletter')::boolean;

-- Events with nested JSON
select occurred_at, action, details from audit.events where action = 'export.run' limit 5;
```

## Files

```text
demo/
├── Containerfile     # postgres:17 plus the scripts
├── compose.yaml      # service jdbx-demo on port 5433
├── 01-schema.sql     # schemas, tables, constraints, indexes, comments
├── 02-data.sql       # deterministic data
├── 03-views.sql
└── README.md
```

The integration test `DemoDatabaseIT` loads `demo/*.sql` and checks the main objects. Run `./mvnw verify` after a change to the scripts.
