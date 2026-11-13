-- Pagebound Books: a fictional online bookshop for demos of jdbx.
-- All data is invented. The official PostgreSQL image runs this file on the first start.

create schema shop;
create schema audit;
create schema ref;

comment on schema shop is 'Catalogue, customers and orders of the bookshop';
comment on schema audit is 'Event log of the shop';
comment on schema ref is 'Reference data: countries and currencies';

-- The demo user finds the shop tables without a schema prefix.
do $$
begin
	execute format('alter database %I set search_path = shop, ref, audit, public', current_database());
end
$$;

-- ref -------------------------------------------------------------------------------------------------------

create table ref.currencies (
	code char(3) primary key,
	name text not null,
	symbol text not null
);
comment on table ref.currencies is 'ISO 4217 currencies the shop accepts';

create table ref.countries (
	code char(2) primary key,
	name text not null,
	region text not null check (region in ('Europe', 'North America', 'South America', 'Asia', 'Oceania', 'Africa')),
	currency_code char(3) not null references ref.currencies (code)
);
comment on table ref.countries is 'ISO 3166 countries the shop ships to';
comment on column ref.countries.code is 'Two-letter country code';

-- shop ------------------------------------------------------------------------------------------------------

create table shop.customers (
	id uuid primary key default gen_random_uuid(),
	email text not null unique,
	full_name text not null,
	country_code char(2) not null references ref.countries (code),
	vip boolean not null default false,
	birthday date,
	created_at timestamptz not null default now(),
	last_login_at timestamptz,
	preferences jsonb not null default '{}',
	notes text
);
comment on table shop.customers is 'People who bought, or could buy, a book';
comment on column shop.customers.vip is 'True for customers with a lifetime spend above the VIP limit';
comment on column shop.customers.preferences is 'Newsletter, language, theme and favourite genres as JSON';
comment on column shop.customers.notes is 'Free text from customer service. Mostly empty.';
create index customers_country_idx on shop.customers (country_code);
create index customers_vip_idx on shop.customers (id) where vip;

create table shop.products (
	id integer generated always as identity primary key,
	sku text not null unique,
	title text not null,
	author text not null,
	category text not null check (category in ('fiction', 'sci-fi', 'non-fiction', 'tech', 'kids', 'cookbook')),
	price numeric(8, 2) not null check (price > 0),
	pages integer not null check (pages > 0),
	published_on date not null,
	tags text[] not null default '{}',
	in_stock boolean not null default true,
	stock_count integer not null default 0,
	description text,
	attributes jsonb not null default '{}'
);
comment on table shop.products is 'The catalogue: one row per book';
comment on column shop.products.sku is 'Stock keeping unit, unique per book';
comment on column shop.products.tags is 'Free tags, for example bestseller or signed';
comment on column shop.products.description is 'Marketing text. Long on purpose: a good test for Cell width.';
create index products_category_idx on shop.products (category, price);

create table shop.orders (
	id bigint generated always as identity primary key,
	customer_id uuid not null references shop.customers (id),
	status text not null check (status in ('new', 'paid', 'shipped', 'delivered', 'cancelled', 'returned')),
	ordered_at timestamptz not null,
	shipped_at timestamptz,
	shipping_address jsonb not null,
	note text
);
comment on table shop.orders is 'One row per customer order';
comment on column shop.orders.shipped_at is 'Empty until the parcel leaves the warehouse';
create index orders_customer_idx on shop.orders (customer_id, ordered_at);
create index orders_open_idx on shop.orders (ordered_at) where status in ('new', 'paid');

create table shop.order_items (
	order_id bigint not null references shop.orders (id),
	line_no integer not null,
	product_id integer not null references shop.products (id),
	quantity integer not null check (quantity > 0),
	unit_price numeric(8, 2) not null,
	primary key (order_id, line_no)
);
comment on table shop.order_items is 'Lines of an order. The unit price is the price at order time.';
create index order_items_product_idx on shop.order_items (product_id);

create table shop.reviews (
	id integer generated always as identity primary key,
	product_id integer not null references shop.products (id),
	customer_id uuid not null references shop.customers (id),
	rating smallint not null check (rating between 1 and 5),
	title text not null,
	body text,
	created_at timestamptz not null,
	unique (product_id, customer_id)
);
comment on table shop.reviews is 'At most one review per customer and book';

-- audit -----------------------------------------------------------------------------------------------------

create table audit.events (
	id bigint generated always as identity primary key,
	occurred_at timestamptz not null,
	actor text not null,
	action text not null,
	entity text not null,
	entity_id text,
	details jsonb
);
comment on table audit.events is 'Event log. More rows than the default Row limit of 1000.';
create index events_occurred_idx on audit.events (occurred_at);
create index events_action_idx on audit.events (action);
