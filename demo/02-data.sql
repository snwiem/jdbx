-- Deterministic data: no random() and no now(). Every start produces the same rows.
-- pg_temp.h(seed, n, m) returns a stable pseudo-random integer in [0, m) derived from md5.

create function pg_temp.h(seed text, n bigint, m integer) returns integer
	language sql immutable
	as $$ select mod(('x' || substr(md5(seed || ':' || n), 1, 7))::bit(28)::integer, m) $$;

-- ref -------------------------------------------------------------------------------------------------------

insert into ref.currencies (code, name, symbol) values
	('EUR', 'Euro', '€'),
	('USD', 'US Dollar', '$'),
	('GBP', 'Pound Sterling', '£'),
	('CHF', 'Swiss Franc', 'CHF'),
	('PLN', 'Zloty', 'zł'),
	('SEK', 'Swedish Krona', 'kr'),
	('NOK', 'Norwegian Krone', 'kr'),
	('DKK', 'Danish Krone', 'kr'),
	('CZK', 'Czech Koruna', 'Kč'),
	('CAD', 'Canadian Dollar', '$'),
	('JPY', 'Yen', '¥'),
	('BRL', 'Real', 'R$'),
	('AUD', 'Australian Dollar', '$'),
	('NZD', 'New Zealand Dollar', '$'),
	('ZAR', 'Rand', 'R');

insert into ref.countries (code, name, region, currency_code) values
	('DE', 'Germany', 'Europe', 'EUR'),
	('AT', 'Austria', 'Europe', 'EUR'),
	('CH', 'Switzerland', 'Europe', 'CHF'),
	('NL', 'Netherlands', 'Europe', 'EUR'),
	('FR', 'France', 'Europe', 'EUR'),
	('ES', 'Spain', 'Europe', 'EUR'),
	('IT', 'Italy', 'Europe', 'EUR'),
	('PL', 'Poland', 'Europe', 'PLN'),
	('SE', 'Sweden', 'Europe', 'SEK'),
	('NO', 'Norway', 'Europe', 'NOK'),
	('DK', 'Denmark', 'Europe', 'DKK'),
	('FI', 'Finland', 'Europe', 'EUR'),
	('IE', 'Ireland', 'Europe', 'EUR'),
	('GB', 'United Kingdom', 'Europe', 'GBP'),
	('PT', 'Portugal', 'Europe', 'EUR'),
	('BE', 'Belgium', 'Europe', 'EUR'),
	('CZ', 'Czechia', 'Europe', 'CZK'),
	('US', 'United States', 'North America', 'USD'),
	('CA', 'Canada', 'North America', 'CAD'),
	('JP', 'Japan', 'Asia', 'JPY'),
	('BR', 'Brazil', 'South America', 'BRL'),
	('AU', 'Australia', 'Oceania', 'AUD'),
	('NZ', 'New Zealand', 'Oceania', 'NZD'),
	('ZA', 'South Africa', 'Africa', 'ZAR');

-- shop.customers: 200 invented people --------------------------------------------------------------------------

insert into shop.customers (id, email, full_name, country_code, vip, birthday, created_at, last_login_at, preferences, notes)
select
	md5('customer-' || n)::uuid,
	lower(translate(first_name, 'ÜüÖöÄäÉéÈèÓóØøËëÅå', 'UuOoAaEeEeOoOoEeAa')) || '.'
		|| lower(translate(last_name, 'ÜüÖöÄäÉéÈèÓóØøËëÅå', 'UuOoAaEeEeOoOoEeAa')) || n || '@example.com',
	first_name || ' ' || last_name,
	(array['DE','DE','DE','AT','CH','NL','FR','ES','IT','PL','SE','NO','DK','FI','IE','GB','GB','PT','BE','CZ','US','US','CA','JP','BR','AU','NZ','ZA'])[1 + pg_temp.h('country', n, 28)],
	pg_temp.h('vip', n, 10) = 0,
	case when pg_temp.h('bday', n, 8) = 0 then null
		else date '1950-01-01' + pg_temp.h('bday-days', n, 365 * 52) end,
	timestamptz '2024-01-01 08:00:00+00' + make_interval(days => pg_temp.h('created', n, 700), hours => pg_temp.h('created-h', n, 14)),
	case when pg_temp.h('login', n, 6) = 0 then null
		else timestamptz '2026-01-01 00:00:00+00' + make_interval(days => pg_temp.h('login-days', n, 300), mins => pg_temp.h('login-min', n, 1440)) end,
	jsonb_build_object(
		'newsletter', pg_temp.h('news', n, 3) > 0,
		'language', (array['de','en','fr','nl','pl'])[1 + pg_temp.h('lang', n, 5)],
		'theme', (array['light','dark','system'])[1 + pg_temp.h('theme', n, 3)],
		'favourite_genres', to_jsonb((array['fiction','sci-fi','non-fiction','tech','kids','cookbook'])[1 + pg_temp.h('g1', n, 6) : 1 + pg_temp.h('g1', n, 6)])),
	case pg_temp.h('note', n, 12)
		when 0 then 'Loves signed editions 📚'
		when 1 then 'Asked for a gift wrap. Twice. 🎁'
		when 2 then 'Prefers delivery to the office (Mon-Fri)'
		when 3 then 'Complained about a damaged cover. Replaced for free.'
		else null end
from (
	select n,
		(array['Anna','Ben','Clara','David','Emma','Felix','Greta','Hannes','Ida','Jürgen','Katja','Lukas','Mila','Noah','Olga','Paul','Quirin','Rosa','Sven','Tilda','Uwe','Vera','Zoë','Søren','Chloé'])[1 + pg_temp.h('first', n, 25)] as first_name,
		(array['Müller','Schmidt','Becker','Weber','Fischer','Wagner','Hoffmann','Koch','Richter','Klein','Wolf','Neumann','Schwarz','Zimmer','Braun','Krüger','Hartmann','Lange','Peters','Lorenz','Berg','Vogel','Keller','Ziegler','Brandt','Åberg'])[1 + pg_temp.h('last', n, 26)] as last_name
	from generate_series(1, 200) as n
) as people;

-- shop.products: 40 invented books ----------------------------------------------------------------------------

insert into shop.products (sku, title, author, category, price, pages, published_on, tags, in_stock, stock_count, description, attributes)
select
	'PB-' || lpad(n::text, 4, '0'),
	title, author, category,
	(9 + pg_temp.h('price', n, 3100)::numeric / 100)::numeric(8, 2),
	80 + pg_temp.h('pages', n, 700),
	date '2008-01-01' + pg_temp.h('pub', n, 365 * 18),
	array_remove(array[
		case when pg_temp.h('best', n, 4) = 0 then 'bestseller' end,
		case when pg_temp.h('sign', n, 6) = 0 then 'signed' end,
		case when pg_temp.h('new', n, 5) = 0 then 'new' end,
		category], null),
	pg_temp.h('stock', n, 9) > 0,
	case when pg_temp.h('stock', n, 9) = 0 then 0 else 1 + pg_temp.h('stock-n', n, 120) end,
	title || ' is a ' || category || ' book by ' || author || '. '
		|| repeat('A careful, slightly funny story that readers keep recommending to their friends and colleagues. ', 1 + pg_temp.h('desc', n, 4)),
	jsonb_build_object(
		'isbn', '978-3-' || lpad(pg_temp.h('isbn', n, 100000)::text, 5, '0') || '-' || pg_temp.h('isbn2', n, 10),
		'format', (array['paperback','hardcover','ebook'])[1 + pg_temp.h('fmt', n, 3)],
		'language', (array['en','de'])[1 + pg_temp.h('plang', n, 2)],
		'dimensions_mm', jsonb_build_array(120 + pg_temp.h('w', n, 60), 190 + pg_temp.h('h', n, 60), 10 + pg_temp.h('t', n, 40)))
from (values
	(1, 'The Quiet Compiler', 'Mara Lindqvist', 'tech'),
	(2, 'Null Pointers and Other Fears', 'Jonas Eberle', 'tech'),
	(3, 'Indexes in the Dark', 'Priya Raman', 'tech'),
	(4, 'Join Me for Dinner', 'Chloé Marchand', 'cookbook'),
	(5, 'The Lazy Transaction', 'Henrik Dahl', 'tech'),
	(6, 'Stack Overflow, Gently', 'Mara Lindqvist', 'tech'),
	(7, 'A Brief History of Cron', 'Tomasz Wrona', 'non-fiction'),
	(8, 'The Last Lighthouse Keeper', 'Ingrid Solberg', 'fiction'),
	(9, 'Orbital Mechanics for Cats', 'Kenji Aoki', 'sci-fi'),
	(10, 'Soup for the Soul and the Build Server', 'Chloé Marchand', 'cookbook'),
	(11, 'The Merge Conflict', 'Jonas Eberle', 'fiction'),
	(12, 'Paper Moons', 'Ida Brandt', 'fiction'),
	(13, 'Little Bear Learns SQL', 'Greta Vogel', 'kids'),
	(14, 'The Garden of Forking Branches', 'Priya Raman', 'sci-fi'),
	(15, 'Slow Queries, Fast Coffee', 'Henrik Dahl', 'tech'),
	(16, 'Mountains Do Not Care', 'Ingrid Solberg', 'non-fiction'),
	(17, 'Bread, Water, Salt', 'Elena Rossi', 'cookbook'),
	(18, 'The Robots Next Door', 'Kenji Aoki', 'sci-fi'),
	(19, 'Counting Sheep in Binary', 'Greta Vogel', 'kids'),
	(20, 'Refactoring the Kitchen', 'Elena Rossi', 'cookbook'),
	(21, 'Letters from a Small Town', 'Ida Brandt', 'fiction'),
	(22, 'Distributed Feelings', 'Tomasz Wrona', 'non-fiction'),
	(23, 'The Cache Invalidation Murders', 'Jonas Eberle', 'fiction'),
	(24, 'Moon Base Zero', 'Kenji Aoki', 'sci-fi'),
	(25, 'Dragons Do Not Use Git', 'Greta Vogel', 'kids'),
	(26, 'Everything Is a Stream', 'Mara Lindqvist', 'tech'),
	(27, 'Thirty Minutes to Pasta', 'Elena Rossi', 'cookbook'),
	(28, 'The Salt Road', 'Ingrid Solberg', 'fiction'),
	(29, 'Ghosts in the Connection Pool', 'Priya Raman', 'tech'),
	(30, 'A Field Guide to Time Zones', 'Tomasz Wrona', 'non-fiction'),
	(31, 'The Seventh Replica', 'Henrik Dahl', 'sci-fi'),
	(32, 'Zoe and the Missing Semicolon', 'Greta Vogel', 'kids'),
	(33, 'Winter at the Observatory', 'Ida Brandt', 'fiction'),
	(34, 'Why We Sleep Badly on Call', 'Tomasz Wrona', 'non-fiction'),
	(35, 'Vacuum Dreams', 'Kenji Aoki', 'sci-fi'),
	(36, 'The Patient Planner', 'Priya Raman', 'tech'),
	(37, 'Pickles and Preserves', 'Elena Rossi', 'cookbook'),
	(38, 'Dear Future Maintainer', 'Mara Lindqvist', 'non-fiction'),
	(39, 'The Great Migration Weekend', 'Jonas Eberle', 'fiction'),
	(40, 'Tiny Services, Big Stories', 'Henrik Dahl', 'tech')
) as books (n, title, author, category);

-- shop.orders: 1200 orders -------------------------------------------------------------------------------------

insert into shop.orders (customer_id, status, ordered_at, shipped_at, shipping_address, note)
select
	md5('customer-' || (1 + pg_temp.h('o-cust', n, 200)))::uuid,
	status,
	ordered_at,
	case when status in ('shipped', 'delivered', 'returned')
		then ordered_at + make_interval(days => 1 + pg_temp.h('ship', n, 3), hours => pg_temp.h('ship-h', n, 12)) end,
	jsonb_build_object(
		'street', (array['Hauptstrasse','Bahnhofstrasse','Gartenweg','Lindenallee','Marktplatz','Schulstrasse'])[1 + pg_temp.h('street', n, 6)] || ' ' || (1 + pg_temp.h('no', n, 120)),
		'city', (array['Berlin','Hamburg','Vienna','Zurich','Utrecht','Lyon','Madrid','Turin','Gdansk','Malmö'])[1 + pg_temp.h('city', n, 10)],
		'zip', lpad(pg_temp.h('zip', n, 99999)::text, 5, '0')),
	case pg_temp.h('o-note', n, 15)
		when 0 then 'Please leave the parcel at the door'
		when 1 then 'Gift: no invoice in the parcel 🎁'
		else null end
from (
	select n,
		timestamptz '2025-01-01 07:30:00+00' + make_interval(hours => n * 7 + pg_temp.h('o-h', n, 7)) as ordered_at,
		case
			when n > 1170 then 'new'
			when n > 1130 then 'paid'
			when n > 1090 then 'shipped'
			else (array['delivered','delivered','delivered','delivered','delivered','delivered','cancelled','returned'])[1 + pg_temp.h('status', n, 8)]
		end as status
	from generate_series(1, 1200) as n
) as numbered;

-- shop.order_items: one to four lines per order ---------------------------------------------------------------

insert into shop.order_items (order_id, line_no, product_id, quantity, unit_price)
select
	o.id,
	l.line_no,
	p.id,
	1 + pg_temp.h('qty', o.id * 10 + l.line_no, 3),
	p.price
from shop.orders o
cross join lateral generate_series(1, 1 + pg_temp.h('lines', o.id, 4)) as l (line_no)
join shop.products p on p.id = 1 + ((pg_temp.h('prod', o.id * 10 + l.line_no, 40) + l.line_no * 7) % 40);

-- shop.reviews: 400 reviews, at most one per customer and book -----------------------------------------------

insert into shop.reviews (product_id, customer_id, rating, title, body, created_at)
select
	1 + ((n - 1) * 7 + ((n - 1) / 200) * 13) % 40,
	md5('customer-' || (1 + (n - 1) % 200))::uuid,
	(array[5, 5, 5, 4, 4, 4, 3, 3, 2, 1])[1 + pg_temp.h('rating', n, 10)],
	(array['Could not put it down', 'Good, but too long', 'A pleasant surprise', 'Not for me', 'My new favourite', 'Funny and wise', 'Arrived with a bent cover', 'Read it twice'])[1 + pg_temp.h('rtitle', n, 8)],
	case when pg_temp.h('rbody', n, 4) = 0 then null
		else repeat('I read this on a long train ride and finished it before the last stop. ', 1 + pg_temp.h('rbody-n', n, 3)) end,
	timestamptz '2025-02-01 12:00:00+00' + make_interval(days => pg_temp.h('rdate', n, 600), mins => pg_temp.h('rdate-m', n, 1440))
from generate_series(1, 400) as n;

-- audit.events: 2500 events -----------------------------------------------------------------------------------

insert into audit.events (occurred_at, actor, action, entity, entity_id, details)
select
	timestamptz '2026-03-01 00:00:00+00' + make_interval(mins => n * 17 + pg_temp.h('e-min', n, 17)),
	(array['system', 'cron', 'web', 'web', 'web', 'support@example.com', 'warehouse@example.com'])[1 + pg_temp.h('actor', n, 7)],
	action,
	split_part(action, '.', 1),
	(1 + pg_temp.h('e-id', n, 1200))::text,
	case action
		when 'order.created' then jsonb_build_object('items', 1 + pg_temp.h('e-items', n, 4), 'channel', (array['web','app','phone'])[1 + pg_temp.h('chan', n, 3)])
		when 'stock.adjusted' then jsonb_build_object('delta', pg_temp.h('delta', n, 41) - 20, 'reason', (array['inventory','damaged','return'])[1 + pg_temp.h('why', n, 3)])
		when 'export.run' then jsonb_build_object('rows', pg_temp.h('rows', n, 90000), 'format', (array['csv','json'])[1 + pg_temp.h('xf', n, 2)], 'nested', jsonb_build_object('ok', pg_temp.h('ok', n, 10) > 0))
		else null end
from (
	select n, (array['order.created','order.paid','order.shipped','customer.login','customer.login','stock.adjusted','review.posted','export.run'])[1 + pg_temp.h('action', n, 8)] as action
	from generate_series(1, 2500) as n
) as numbered;

analyze;
