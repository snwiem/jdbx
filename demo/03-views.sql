-- Views of the shop. v_customer_overview is very wide on purpose: use /orientation vertical.

create view shop.v_order_totals as
select
	o.id as order_id,
	c.full_name as customer,
	o.status,
	o.ordered_at,
	o.shipped_at,
	count(i.line_no) as line_count,
	sum(i.quantity) as item_count,
	sum(i.quantity * i.unit_price) as total
from shop.orders o
join shop.customers c on c.id = o.customer_id
join shop.order_items i on i.order_id = o.id
group by o.id, c.full_name, o.status, o.ordered_at, o.shipped_at;
comment on view shop.v_order_totals is 'One row per order with the number of items and the total';

create view shop.v_top_products as
select
	p.id as product_id,
	p.title,
	p.author,
	p.category,
	p.price,
	coalesce(s.units_sold, 0) as units_sold,
	coalesce(s.revenue, 0) as revenue,
	r.review_count,
	r.avg_rating
from shop.products p
left join (
	select product_id, sum(quantity) as units_sold, sum(quantity * unit_price) as revenue
	from shop.order_items
	group by product_id
) s on s.product_id = p.id
left join (
	select product_id, count(*) as review_count, round(avg(rating), 2) as avg_rating
	from shop.reviews
	group by product_id
) r on r.product_id = p.id;
comment on view shop.v_top_products is 'Sales and review numbers per book. Sort it by revenue.';

create view shop.v_customer_overview as
select
	c.id as customer_id,
	c.email,
	c.full_name,
	c.country_code,
	k.name as country_name,
	k.region,
	k.currency_code,
	c.vip,
	c.birthday,
	extract(year from age(date '2026-06-30', c.birthday))::integer as age,
	c.created_at,
	c.last_login_at,
	c.preferences ->> 'language' as language,
	c.preferences ->> 'theme' as theme,
	(c.preferences ->> 'newsletter')::boolean as newsletter,
	coalesce(o.order_count, 0) as order_count,
	coalesce(o.total_spent, 0) as total_spent,
	o.first_order_at,
	o.last_order_at,
	round(o.total_spent / nullif(o.order_count, 0), 2) as avg_order_value,
	coalesce(r.review_count, 0) as review_count,
	r.avg_rating,
	c.notes
from shop.customers c
join ref.countries k on k.code = c.country_code
left join (
	select o.customer_id, count(distinct o.id) as order_count, sum(i.quantity * i.unit_price) as total_spent,
		min(o.ordered_at) as first_order_at, max(o.ordered_at) as last_order_at
	from shop.orders o
	join shop.order_items i on i.order_id = o.id
	group by o.customer_id
) o on o.customer_id = c.id
left join (
	select customer_id, count(*) as review_count, round(avg(rating), 2) as avg_rating
	from shop.reviews
	group by customer_id
) r on r.customer_id = c.id;
comment on view shop.v_customer_overview is 'One very wide row per customer: a good test for /orientation vertical';
