--
-- PostgreSQL database dump
--

\restrict GIl9rMsnj1zMin7raFuvme2FYy8GSqYEfWxINr7VcbNlhV4P4NknxS3WfwUlgec

-- Dumped from database version 16.13
-- Dumped by pg_dump version 16.13

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: areas; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.areas (
    created_at timestamp(6) without time zone,
    id uuid NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL,
    salesman_id uuid
);


ALTER TABLE public.areas OWNER TO postgres;

--
-- Name: bill_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bill_items (
    free_quantity integer,
    gst_amount numeric(38,2),
    gst_percent numeric(38,2),
    quantity integer NOT NULL,
    rate numeric(38,2),
    total numeric(38,2),
    batch_id uuid,
    bill_id uuid,
    id uuid NOT NULL,
    product_id uuid,
    unit_type character varying(255),
    cess_amount numeric(38,2),
    cess_percent numeric(38,2),
    CONSTRAINT bill_items_unit_type_check CHECK (((unit_type)::text = ANY (ARRAY[('BOX'::character varying)::text, ('LADI'::character varying)::text, ('PACK'::character varying)::text, ('BOTTLE'::character varying)::text, ('CRATE'::character varying)::text])))
);


ALTER TABLE public.bill_items OWNER TO postgres;

--
-- Name: bills; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.bills (
    discount numeric(38,2),
    grand_total numeric(38,2),
    gst_total numeric(38,2),
    paid_amount numeric(38,2),
    pending_amount numeric(38,2),
    subtotal numeric(38,2),
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    created_by uuid,
    customer_id uuid,
    id uuid NOT NULL,
    bill_number character varying(255),
    notes character varying(255),
    payment_mode character varying(255),
    status character varying(255),
    cess_total numeric(38,2),
    CONSTRAINT bills_payment_mode_check CHECK (((payment_mode)::text = ANY (ARRAY[('CASH'::character varying)::text, ('UPI'::character varying)::text, ('UDHAR'::character varying)::text, ('PARTIAL'::character varying)::text]))),
    CONSTRAINT bills_status_check CHECK (((status)::text = ANY (ARRAY[('DRAFT'::character varying)::text, ('CONFIRMED'::character varying)::text, ('PARTIAL'::character varying)::text, ('PAID'::character varying)::text, ('CANCELLED'::character varying)::text])))
);


ALTER TABLE public.bills OWNER TO postgres;

--
-- Name: customers; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.customers (
    is_active boolean,
    is_npa boolean,
    latitude double precision,
    longitude double precision,
    opening_balance numeric(38,2),
    total_pending numeric(38,2),
    created_at timestamp(6) without time zone,
    last_order_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    area_id uuid,
    id uuid NOT NULL,
    customer_code character varying(255),
    location_method character varying(255),
    name character varying(255) NOT NULL,
    phone character varying(255),
    shop_name character varying(255),
    credit_limit numeric(38,2)
);


ALTER TABLE public.customers OWNER TO postgres;

--
-- Name: damage_log; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.damage_log (
    quantity integer,
    value_loss numeric(38,2),
    logged_at timestamp(6) without time zone,
    batch_id uuid,
    id uuid NOT NULL,
    logged_by uuid,
    product_id uuid,
    notes character varying(255),
    reason character varying(255),
    unit_type character varying(255),
    claim_status character varying(255),
    unit_level character varying(255),
    CONSTRAINT damage_log_claim_status_check CHECK (((claim_status)::text = ANY (ARRAY[('CLAIMABLE'::character varying)::text, ('PERMANENT_LOSS'::character varying)::text, ('NON_CLAIMABLE'::character varying)::text]))),
    CONSTRAINT damage_log_reason_check CHECK (((reason)::text = ANY (ARRAY[('LEAK'::character varying)::text, ('CRUSH'::character varying)::text, ('EXPIRE'::character varying)::text, ('OTHER'::character varying)::text]))),
    CONSTRAINT damage_log_unit_level_check CHECK (((unit_level)::text = ANY (ARRAY[('PRIMARY'::character varying)::text, ('SECONDARY'::character varying)::text, ('SINGLE'::character varying)::text]))),
    CONSTRAINT damage_log_unit_type_check CHECK (((unit_type)::text = ANY (ARRAY[('BOX'::character varying)::text, ('LADI'::character varying)::text, ('PACK'::character varying)::text, ('BOTTLE'::character varying)::text, ('CRATE'::character varying)::text])))
);


ALTER TABLE public.damage_log OWNER TO postgres;

--
-- Name: deliveries; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.deliveries (
    cash_collected numeric(38,2),
    scheduled_date date,
    created_at timestamp(6) without time zone,
    delivered_at timestamp(6) without time zone,
    dispatched_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    bill_id uuid,
    delivery_boy_id uuid,
    id uuid NOT NULL,
    notes character varying(255),
    status character varying(255),
    type character varying(255),
    CONSTRAINT deliveries_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PACKED'::character varying)::text, ('OUT'::character varying)::text, ('DELIVERED'::character varying)::text, ('FAILED'::character varying)::text, ('PARTIAL'::character varying)::text]))),
    CONSTRAINT deliveries_type_check CHECK (((type)::text = ANY (ARRAY[('SAME_DAY'::character varying)::text, ('SCHEDULED'::character varying)::text, ('SELF_PICKUP'::character varying)::text])))
);


ALTER TABLE public.deliveries OWNER TO postgres;

--
-- Name: expenses; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.expenses (
    amount numeric(38,2) NOT NULL,
    expense_date date NOT NULL,
    created_at timestamp(6) without time zone,
    added_by uuid,
    id uuid NOT NULL,
    category character varying(255) NOT NULL,
    description character varying(255),
    recipient_id uuid,
    CONSTRAINT expenses_category_check CHECK (((category)::text = ANY ((ARRAY['FUEL'::character varying, 'SALARY'::character varying, 'PACKAGING'::character varying, 'RENT'::character varying, 'ELECTRICITY'::character varying, 'STOCK_PURCHASE'::character varying, 'VEHICLE_MAINTENANCE'::character varying, 'LABOR_AND_LOADING'::character varying, 'OTHER'::character varying])::text[])))
);


ALTER TABLE public.expenses OWNER TO postgres;

--
-- Name: payments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.payments (
    amount numeric(38,2),
    paid_at timestamp(6) without time zone,
    bill_id uuid,
    collected_by uuid,
    customer_id uuid,
    id uuid NOT NULL,
    notes character varying(255),
    payment_mode character varying(255),
    adjusted_bill_id uuid,
    adjustment_note character varying(255),
    adjustment_type character varying(255),
    applied_amount numeric(38,2),
    excess_amount numeric(38,2),
    CONSTRAINT payments_adjustment_type_check CHECK (((adjustment_type)::text = ANY (ARRAY[('NORMAL'::character varying)::text, ('MANUAL_ADJUST'::character varying)::text, ('AUTO_ADJUST'::character varying)::text])))
);


ALTER TABLE public.payments OWNER TO postgres;

--
-- Name: products; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.products (
    buy_price_with_tax numeric(38,2),
    buy_price_without_tax numeric(38,2),
    can_sell_primary boolean,
    can_sell_secondary boolean,
    gst_percent numeric(38,2),
    is_active boolean,
    low_stock_alert integer,
    secondary_per_primary integer,
    sell_price_primary numeric(38,2),
    sell_price_secondary numeric(38,2),
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    brand character varying(255),
    category character varying(255) NOT NULL,
    low_stock_unit character varying(255),
    name character varying(255) NOT NULL,
    primary_unit character varying(255),
    product_code character varying(255),
    secondary_unit character varying(255),
    cess_percent numeric(38,2),
    other_category_detail character varying(255)
);


ALTER TABLE public.products OWNER TO postgres;

--
-- Name: stock; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.stock (
    has_open_primary boolean,
    open_primary_remaining integer,
    total_primary_units integer,
    total_secondary_units integer,
    last_updated timestamp(6) without time zone,
    id uuid NOT NULL,
    product_id uuid
);


ALTER TABLE public.stock OWNER TO postgres;

--
-- Name: stock_adjustment_logs; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.stock_adjustment_logs (
    id uuid NOT NULL,
    adjusted_by character varying(255) NOT NULL,
    batch_id uuid NOT NULL,
    batch_number character varying(255) NOT NULL,
    new_secondary_remaining integer NOT NULL,
    old_secondary_remaining integer NOT NULL,
    product_name character varying(255) NOT NULL,
    reason character varying(255) NOT NULL,
    "timestamp" timestamp(6) without time zone NOT NULL
);


ALTER TABLE public.stock_adjustment_logs OWNER TO postgres;

--
-- Name: stock_batches; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.stock_batches (
    buy_price_with_tax numeric(38,2),
    buy_price_without_tax numeric(38,2),
    expiry_date date,
    gst_percent numeric(38,2),
    is_exhausted boolean,
    primary_received integer,
    secondary_received integer,
    secondary_remaining integer,
    received_at timestamp(6) without time zone,
    id uuid NOT NULL,
    product_id uuid,
    batch_number character varying(255),
    supplier_name character varying(255),
    secondary_soft_reserved integer
);


ALTER TABLE public.stock_batches OWNER TO postgres;

--
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    is_active boolean,
    must_change_password boolean,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    phone character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    last_latitude double precision,
    last_location_time timestamp(6) without time zone,
    last_longitude double precision,
    monthly_salary numeric(38,2)
);


ALTER TABLE public.users OWNER TO postgres;

--
-- Data for Name: areas; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.areas (created_at, id, description, name, salesman_id) FROM stdin;
2026-06-10 18:16:15.794256	26bcb20d-32e2-4041-bb77-7ca14dacdc71	All Gauri Bazar	Gauri Bazar	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:16:47.902849	6c482904-634b-4286-a5b8-3a04d3bb61d3		Hata Road Gauri Bazar	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:17:37.345978	66e38e39-fbc8-4c2d-aeba-fcb8482a3476	Kakwal Hata Road	Kakwal Hata Road	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:18:21.096337	4168d5dc-73ae-453c-bc9b-f0da53e8a475		Bishanpura Hata Road	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:18:47.196242	2ff4b302-7719-414b-9336-36b8279681f8		Bakhara Hata Road	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:18:58.530521	675babe9-fa73-4903-bd07-a3953e7cabed		Wkeelganj Hata Road	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:19:34.786778	43d3f9c8-4c0e-4880-b4b7-d9b4ec93de12		Khroh Gorakhpur Deoria Road	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:20:14.853148	05fd82db-f318-4877-b49e-b3e7090fbcd6		Mathiya Rudarpur  Road	35a9ce32-7435-4bc3-8bff-0131d8bff791
2026-06-10 18:22:25.724509	7ee0d59b-fe33-46f7-89f6-2d95a75419af		Indupur	35a9ce32-7435-4bc3-8bff-0131d8bff791
\.


--
-- Data for Name: bill_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.bill_items (free_quantity, gst_amount, gst_percent, quantity, rate, total, batch_id, bill_id, id, product_id, unit_type, cess_amount, cess_percent) FROM stdin;
\.


--
-- Data for Name: bills; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.bills (discount, grand_total, gst_total, paid_amount, pending_amount, subtotal, created_at, updated_at, created_by, customer_id, id, bill_number, notes, payment_mode, status, cess_total) FROM stdin;
\.


--
-- Data for Name: customers; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.customers (is_active, is_npa, latitude, longitude, opening_balance, total_pending, created_at, last_order_at, updated_at, area_id, id, customer_code, location_method, name, phone, shop_name, credit_limit) FROM stdin;
t	f	\N	\N	20133.00	20133.00	2026-06-10 18:24:00.631411	\N	2026-06-10 18:24:00.631411	7ee0d59b-fe33-46f7-89f6-2d95a75419af	68e618ca-1aad-4a13-98c5-8e2253613af0	CUST-00001	\N	Jitendar Jaiswal	9936364872	Jitendar Kirana	50000.00
t	f	\N	\N	0.00	0.00	2026-06-11 17:38:55.837343	\N	2026-06-11 17:38:55.837343	26bcb20d-32e2-4041-bb77-7ca14dacdc71	6508837d-5b3e-4a87-a4db-f9831fd223f4	CUST-00002	\N	Akash Gupta	9123457879	Akash Kirana	50000.00
\.


--
-- Data for Name: damage_log; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.damage_log (quantity, value_loss, logged_at, batch_id, id, logged_by, product_id, notes, reason, unit_type, claim_status, unit_level) FROM stdin;
\.


--
-- Data for Name: deliveries; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.deliveries (cash_collected, scheduled_date, created_at, delivered_at, dispatched_at, updated_at, bill_id, delivery_boy_id, id, notes, status, type) FROM stdin;
\.


--
-- Data for Name: expenses; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.expenses (amount, expense_date, created_at, added_by, id, category, description, recipient_id) FROM stdin;
3053.54	2026-06-10	2026-06-10 14:30:40.734497	\N	3686db99-caff-4006-a823-290942e75c94	STOCK_PURCHASE	Stock Purchase: 2 BOX of All in one-70/ Rs-20 from M/S LARI TRADERS (Batch: DAFE22)	\N
3053.54	2026-06-10	2026-06-10 14:30:40.734497	\N	55e0f258-5b4e-4307-b212-1a23d91801f5	STOCK_PURCHASE	Stock Purchase: 2 BOX of Aalu Bhujiya-75Gm/Rs-20 from M/S LARI TRADERS (Batch: MAFE30)	\N
4580.31	2026-06-10	2026-06-10 14:30:40.734497	\N	dc8c12ba-85d5-4f67-b166-9547c42205aa	STOCK_PURCHASE	Stock Purchase: 3 BOX of Aalu Bhujiya-20Gm/Rs-5 from M/S LARI TRADERS (Batch: MAFE24)	\N
16202.90	2026-06-10	2026-06-10 14:30:40.734497	\N	6c56199a-3889-44ed-b150-f0e937327a9d	STOCK_PURCHASE	Stock Purchase: 10 BOX of Bhujiya-17Gm/Rs-5 from M/S LARI TRADERS (Batch: HAFE31A)	\N
9160.62	2026-06-10	2026-06-10 14:30:40.734497	\N	0559627b-7f86-473e-90d1-41f8fd99a37a	STOCK_PURCHASE	Stock Purchase: 6 BOX of Khatta Meetha-20Gm/Rs-5 from M/S LARI TRADERS (Batch: HAFE23B)	\N
20253.60	2026-06-10	2026-06-10 14:30:40.734497	\N	6bbf398b-56a1-4e7a-ae94-eb2c1721f8e8	STOCK_PURCHASE	Stock Purchase: 10 BOX of Lite Mixture-20Gm/Rs-5 from M/S LARI TRADERS (Batch: DAFE23)	\N
4050.72	2026-06-10	2026-06-10 14:30:40.734497	\N	5c15505e-a401-4db5-a774-0fe91bbb596a	STOCK_PURCHASE	Stock Purchase: 2 BOX of Lite Jhal Muri-20Gm/Rs-5 from M/S LARI TRADERS (Batch: DAFE27)	\N
4050.72	2026-06-10	2026-06-10 14:30:40.734497	\N	42af9dd4-39db-4d08-80b5-fe38ef93b6ce	STOCK_PURCHASE	Stock Purchase: 2 BOX of Lite Mixture-20Gm/Rs-5 from M/S LARI TRADERS (Batch: PBFD23)	\N
3673.92	2026-06-10	2026-06-10 14:30:40.734497	\N	4e2849be-09d6-46c8-bd10-df16d548b6fb	STOCK_PURCHASE	Stock Purchase: 3 BOX of Moong Dal-70Gm/Rs-20 from M/S LARI TRADERS (Batch: PBFE24)	\N
14601.40	2026-06-10	2026-06-10 14:30:40.734497	\N	71f2ada7-e5ad-4732-b98e-d1b585cb4978	STOCK_PURCHASE	Stock Purchase: 10 BOX of Moong Dal-17Gm/Rs-5 from M/S LARI TRADERS (Batch: HAFE19A)	\N
4580.31	2026-06-10	2026-06-10 14:30:40.734497	\N	b8ff1e3f-3e0c-4429-8309-988268651ce3	STOCK_PURCHASE	Stock Purchase: 3 BOX of Navaratan-20Gm/Rs-5 from M/S LARI TRADERS (Batch: HAFE28B)	\N
30380.40	2026-06-10	2026-06-10 14:30:40.734497	\N	ce0465dd-edd5-4f3a-b8eb-1b75ab853d59	STOCK_PURCHASE	Stock Purchase: 15 BOX of Navaratan-75Gm/Rs-20 from M/S LARI TRADERS (Batch: DAFE23)	\N
3053.54	2026-06-10	2026-06-10 14:30:40.734497	\N	41224c9d-2664-4ebd-8c9a-c61fd4e880d4	STOCK_PURCHASE	Stock Purchase: 2 BOX of Nut Cracker-70Gm/Rs-20 from M/S LARI TRADERS (Batch: RBFE27)	\N
2025.36	2026-06-10	2026-06-10 14:30:40.734497	\N	1bbb0741-e434-4437-b3f8-13773b600e77	STOCK_PURCHASE	Stock Purchase: 1 BOX of Punjabi Tadka-20Gm/Rs-5 from M/S LARI TRADERS (Batch: DAFE22)	\N
12152.16	2026-06-10	2026-06-10 14:30:40.734497	\N	5c9aa216-21b3-4249-8c66-8c5e013ed6d6	STOCK_PURCHASE	Stock Purchase: 6 BOX of Ratlami Mix-75Gm/Rs-20 from M/S LARI TRADERS (Batch: MAFE22)	\N
3485.50	2026-06-10	2026-06-10 14:30:40.734497	\N	6d2e263d-733a-4ead-aff4-4b112781750f	STOCK_PURCHASE	Stock Purchase: 2 BOX of Pinut Salted-16Gm/Rs-5 from M/S LARI TRADERS (Batch: PAFE27)	\N
3053.54	2026-06-10	2026-06-10 14:30:40.734497	\N	1a11cc15-7701-403d-ac64-439cc7629063	STOCK_PURCHASE	Stock Purchase: 2 BOX of Pinut Salted-16Gm/Rs-5 from M/S LARI TRADERS (Batch: RBFE26)	\N
\.


--
-- Data for Name: payments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.payments (amount, paid_at, bill_id, collected_by, customer_id, id, notes, payment_mode, adjusted_bill_id, adjustment_note, adjustment_type, applied_amount, excess_amount) FROM stdin;
\.


--
-- Data for Name: products; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.products (buy_price_with_tax, buy_price_without_tax, can_sell_primary, can_sell_secondary, gst_percent, is_active, low_stock_alert, secondary_per_primary, sell_price_primary, sell_price_secondary, created_at, updated_at, id, brand, category, low_stock_unit, name, primary_unit, product_code, secondary_unit, cess_percent, other_category_detail) FROM stdin;
1980.68	1886.36	t	t	5.00	t	10	42	1981.00	47.70	2026-06-09 00:08:46.448033	2026-06-11 18:45:26.296561	e1124468-d696-451a-a0a9-4d576dab38af	Haldiram's	NAMKEEN	SECONDARY	Moong Dal-17Gm/Rs-5	BOX	PROD-00014	LADI	0.00	
1979.63	1885.36	t	t	5.00	t	10	42	1981.00	47.70	2026-06-09 00:12:30.210246	2026-06-11 18:46:12.746127	e0a8a110-9676-4cc5-9287-2fa76ef2883f	Haldiram's	NAMKEEN	SECONDARY	Nut Cracker-18Gm/Rs-5	BOX	PROD-00016	LADI	0.00	
780.36	743.20	t	t	5.00	t	10	16	760.00	48.51	2026-06-09 00:38:21.064685	2026-06-11 17:35:33.44944	cbc6d684-f556-467b-933f-e83e8450f333	Haldiram's	SNACKS	SECONDARY	Panga Tangy-16Gm/Rs-5	BOX	PROD-00025	LADI	0.00	
63.08	60.08	t	t	5.00	t	10	1	90.00	85.00	2026-06-09 00:46:29.905685	2026-06-09 00:46:29.905685	006241eb-449a-4fbd-ae99-5c5e5a269f61	Haldiram's	OTHER	SECONDARY	Utsav Box	BOX	PROD-00029	PACK	0.00	Celebration Pack
7.33	6.98	t	t	5.00	t	10	60	7.84	7.50	2026-06-09 00:48:50.479522	2026-06-09 00:48:50.479522	5852d3a6-892f-423f-8d09-6b8f00321811	Haldiram's	BISCUITS	SECONDARY	Rusk Bread Toast-50Gm/Rs-10	BOX	PROD-00030	PACK	0.00	
1526.77	1454.07	t	t	5.00	t	10	10	1571.50	161.90	2026-06-08 23:49:09.801598	2026-06-11 19:01:56.347145	d6cb115a-202c-4675-919c-11106bfbcc23	Haldiram's	NAMKEEN	SECONDARY	Aalu Bhujiya-75Gm/Rs-20	BOX	PROD-00003	LADI	0.00	
551.78	525.50	t	t	5.00	t	10	12	570.00	48.45	2026-06-09 00:40:37.430646	2026-06-11 17:41:21.339884	1f9ba013-a347-4a10-85cc-32d50fb89798	Haldiram's	SNACKS	SECONDARY	Snaclite Katori 20Gm/Rs-5	BOX	PROD-00026	LADI	0.00	
551.78	525.50	t	t	5.00	t	10	12	570.00	48.45	2026-06-09 00:44:04.056251	2026-06-11 17:42:46.781476	1c6cab17-0f56-4887-9b09-9227d912358a	Haldiram's	SNACKS	SECONDARY	Snaclite Fun Swing-20Gm/Rs-5	BOX	PROD-00028	LADI	0.00	
975.21	928.77	t	t	5.00	t	10	20	1000.00	48.60	2026-06-09 00:36:43.415968	2026-06-11 19:40:06.117929	743a7aa3-3b1c-4e0c-95d4-46a0f06a07e1	Haldiram's	CHIPS	SECONDARY	Chips Thai Chill-13Gm/Rs-5	BOX	PROD-00024	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	42	1981.00	47.70	2026-06-09 00:03:23.412972	2026-06-11 18:43:44.226362	b3794c3d-ceb1-4738-a192-140e8e620e7e	Haldiram's	NAMKEEN	SECONDARY	Heend Jeera Matar-20Gm/Rs-5	BOX	PROD-00010	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	25	1180.00	47.70	2026-06-09 00:06:28.35072	2026-06-11 18:49:15.706081	f4eb6e9a-c2ad-4279-9caa-d26d6907f5e8	Haldiram's	NAMKEEN	SECONDARY	Lite Jhal Muri-20Gm/Rs-5	BOX	PROD-00012	LADI	0.00	
975.21	928.77	t	t	5.00	t	10	20	1000.00	51.00	2026-06-09 00:23:28.122708	2026-06-11 19:38:34.86022	5fcac496-07c6-4481-a867-2c2df66c54d1	Haldiram's	CHIPS	SECONDARY	Chips Classic Salted-13Gm/Rs-5	BOX	PROD-00019	LADI	0.00	
975.21	928.77	t	t	5.00	t	10	20	1000.00	51.00	2026-06-09 00:32:19.719536	2026-06-11 19:38:50.148809	060c8c01-c136-412f-9dbb-fb5eef9c873c	Haldiram's	CHIPS	SECONDARY	Chips Chilli Sprinkler-13Gm/Rs-5	BOX	PROD-00021	LADI	0.00	
975.21	928.77	t	t	5.00	t	10	20	1000.00	51.00	2026-06-09 00:25:50.698624	2026-06-11 19:39:19.298559	29fdb617-7c54-4fb2-800a-d615a02dac9b	Haldiram's	CHIPS	SECONDARY	Chips Cream Onion-13Gm/Rs-5	BOX	PROD-00020	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	42	1981.00	47.70	2026-06-08 23:59:21.375051	2026-06-11 18:50:01.739644	c8f57c3a-8a0c-471c-9997-505eefa4d11b	Haldiram's	NAMKEEN	SECONDARY	Aalu Bhujiya-20Gm/Rs-5	BOX	PROD-00008	LADI	0.00	
975.21	928.77	t	t	5.00	t	10	20	1000.00	48.60	2026-06-09 00:33:46.838655	2026-06-11 19:39:46.764626	e263acdc-4fd7-43a1-9f71-9fff02c12faa	Haldiram's	CHIPS	SECONDARY	Chips Masala-13Gm/Rs-5	BOX	PROD-00022	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	42	1981.00	47.70	2026-06-09 00:01:06.625198	2026-06-11 18:50:35.21337	854cae54-9590-4795-87a8-7faef48e2f82	Haldiram's	NAMKEEN	SECONDARY	Bhujiya-17Gm/Rs-5	BOX	PROD-00009	LADI	0.00	
975.21	928.77	t	t	5.00	t	10	20	1000.00	48.60	2026-06-09 00:35:17.92649	2026-06-11 19:39:55.759143	c8233574-2fff-4ead-92ce-8f5f57998528	Haldiram's	CHIPS	SECONDARY	Chips Pudina-13Gm/Rs-5	BOX	PROD-00023	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	42	1981.00	47.70	2026-06-09 00:05:01.171283	2026-06-11 18:50:59.209627	45c4235e-0e6d-43fb-9576-3266fd7e1049	Haldiram's	NAMKEEN	SECONDARY	Khatta Meetha-20Gm/Rs-5	BOX	PROD-00011	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	42	1981.00	47.70	2026-06-09 00:10:52.787528	2026-06-11 18:51:30.834688	9b9327c5-599f-4b7f-9d9b-cfeb92735c48	Haldiram's	NAMKEEN	SECONDARY	Navaratan-20Gm/Rs-5	BOX	PROD-00015	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	30	1415.30	47.70	2026-06-09 00:07:36.815533	2026-06-11 18:53:45.026973	4cb63981-7b93-4c8b-b70b-2a4938d34c7f	Haldiram's	NAMKEEN	SECONDARY	Lite Mixture-20Gm/Rs-5	BOX	PROD-00013	LADI	0.00	
1526.77	1454.07	t	t	5.00	t	10	10	1571.50	161.91	2026-06-08 23:54:01.397689	2026-06-11 18:56:25.490284	9bbc81b5-e7f3-4bce-9636-d2b45274bd6d	Haldiram's	NAMKEEN	SECONDARY	Moong Dal-70Gm/Rs-20	BOX	PROD-00005	LADI	0.00	
1526.77	1454.07	t	t	5.00	t	10	10	1571.50	161.91	2026-06-08 23:54:59.227737	2026-06-11 18:58:18.271681	43792c80-446b-48c5-b107-4a8fc91268df	Haldiram's	NAMKEEN	SECONDARY	Navaratan-75Gm/Rs-20	BOX	PROD-00006	LADI	0.00	
1526.77	1454.07	t	t	5.00	t	10	10	1571.50	161.91	2026-06-08 23:51:37.188708	2026-06-11 18:59:02.569979	d6452eae-49dd-4be4-a80e-30c8c2dc3de5	Haldiram's	NAMKEEN	SECONDARY	Bhujiya-70Gm/Rs-20	BOX	PROD-00004	LADI	0.00	
894.87	852.26	t	t	5.00	t	10	20	912.00	48.45	2026-06-08 00:29:20.380506	2026-06-11 17:30:04.45945	f4c27037-19dd-46ef-9218-c57c23c36c09	Haldiram's	SNACKS	SECONDARY	Takatak Masala-20Gm/Rs-5	BOX	PROD-00001	LADI	0.00	
551.78	525.50	t	t	5.00	t	10	12	570.00	48.45	2026-06-09 00:42:28.556109	2026-06-11 17:33:34.985967	57de4cf6-3a60-4e63-826b-1fad19adde76	Haldiram's	SNACKS	SECONDARY	Snaclite Finger-20Gm/Rs-5	BOX	PROD-00027	LADI	0.00	
1526.77	1454.07	t	t	5.00	t	10	10	1571.50	161.91	2026-06-08 23:47:42.901445	2026-06-11 18:59:40.898509	c78b7f7d-11a2-45a9-ad15-e177df66b97f	Haldiram's	NAMKEEN	SECONDARY	All in one-70/ Rs-20	BOX	PROD-00002	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	42	2080.00	50.00	2026-06-09 00:14:55.837061	2026-06-10 17:54:51.124217	21f5e577-dded-45a7-bd8a-93f84da36836	Haldiram's	NAMKEEN	SECONDARY	Punjabi Tadka-20Gm/Rs-5	BOX	PROD-00017	LADI	0.00	
1980.68	1886.36	t	t	5.00	t	10	42	2080.00	50.00	2026-06-09 00:16:23.579869	2026-06-10 17:54:51.124217	ff42030d-7e22-49a9-b827-bbce60ecb522	Haldiram's	NAMKEEN	SECONDARY	Pinut Salted-16Gm/Rs-5	BOX	PROD-00018	LADI	0.00	
1526.77	1454.07	t	t	5.00	t	10	10	1650.00	170.00	2026-06-08 23:56:22.303831	2026-06-10 17:58:18.176247	3768a7cc-c7fd-499e-8911-bf90ecb516ea	Haldiram's	NAMKEEN	SECONDARY	Ratlami Mix-75Gm/Rs-20	BOX	PROD-00007	LADI	0.00	
1526.77	1454.07	t	t	5.00	t	10	10	1650.00	170.00	2026-06-10 13:38:55.162904	2026-06-11 18:08:08.042714	ff086cc2-fc46-4ae5-8cb7-036df9b4ac21	Haldiram's	NAMKEEN	SECONDARY	Nut Cracker-70Gm/Rs-20	BOX	PROD-00031	LADI	0.00	
\.


--
-- Data for Name: stock; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.stock (has_open_primary, open_primary_remaining, total_primary_units, total_secondary_units, last_updated, id, product_id) FROM stdin;
f	0	38	760	2026-06-08 00:37:44.889521	d7767434-53f9-4bb1-9f7f-6606982b3c50	f4c27037-19dd-46ef-9218-c57c23c36c09
f	0	0	0	2026-06-08 23:51:37.208411	7d759bc6-8387-4e5e-be16-4a38def67a52	d6452eae-49dd-4be4-a80e-30c8c2dc3de5
f	0	0	0	2026-06-09 00:03:23.422976	1a122695-201e-483c-8a05-54b82e0363a4	b3794c3d-ceb1-4738-a192-140e8e620e7e
f	0	0	0	2026-06-09 00:12:30.215908	38f2c410-4941-49bb-ba5b-fb0174fb4e9b	e0a8a110-9676-4cc5-9287-2fa76ef2883f
f	0	0	0	2026-06-09 00:23:28.127709	6b343adc-f7b7-4d39-92b7-102bec849317	5fcac496-07c6-4481-a867-2c2df66c54d1
f	0	0	0	2026-06-09 00:25:50.703638	f354d03d-43d3-4c5f-924b-20d5a2c1b15f	29fdb617-7c54-4fb2-800a-d615a02dac9b
f	0	0	0	2026-06-09 00:32:19.735146	1792d301-2f92-4465-a0ed-bfd5ad7ce48f	060c8c01-c136-412f-9dbb-fb5eef9c873c
f	0	0	0	2026-06-09 00:33:46.844657	f76df58a-3619-473e-aab1-14fca01c9ae1	e263acdc-4fd7-43a1-9f71-9fff02c12faa
f	0	0	0	2026-06-09 00:35:17.930526	e445c0d4-db84-4f43-abe3-7a56845b2071	c8233574-2fff-4ead-92ce-8f5f57998528
f	0	0	0	2026-06-09 00:36:43.420482	1f3502de-1fb9-4443-a2cb-7779aaba8eb2	743a7aa3-3b1c-4e0c-95d4-46a0f06a07e1
f	0	0	0	2026-06-09 00:38:21.069456	9c2507bd-440e-4090-93f2-13a6086e1491	cbc6d684-f556-467b-933f-e83e8450f333
f	0	0	0	2026-06-09 00:40:37.435649	4d01e6f4-1a65-44c8-8a23-80a1d551e5bc	1f9ba013-a347-4a10-85cc-32d50fb89798
f	0	0	0	2026-06-09 00:42:28.55912	76789063-c1b8-4971-8848-4763450e6861	57de4cf6-3a60-4e63-826b-1fad19adde76
f	0	0	0	2026-06-09 00:44:04.058708	8fd5fccd-f634-4bb6-9f21-b0fba6fae96f	1c6cab17-0f56-4887-9b09-9227d912358a
f	0	0	0	2026-06-09 00:46:29.908692	923f73c4-e184-42d7-b668-d2f4d3c2a9c7	006241eb-449a-4fbd-ae99-5c5e5a269f61
f	0	0	0	2026-06-09 00:48:50.482977	3252a90b-f3f8-415a-9192-e41fe897c4b8	5852d3a6-892f-423f-8d09-6b8f00321811
f	0	3	126	2026-06-10 14:30:40.734497	87603b37-7559-4975-9057-01e9c91f9b77	c8f57c3a-8a0c-471c-9997-505eefa4d11b
f	0	10	420	2026-06-10 14:30:40.734497	2f429219-00d1-443a-9ccc-82e916f039f9	854cae54-9590-4795-87a8-7faef48e2f82
f	0	6	252	2026-06-10 14:30:40.734497	c24148c8-2811-49f7-bd0f-aa268cbd36b5	45c4235e-0e6d-43fb-9576-3266fd7e1049
f	0	3	30	2026-06-10 14:30:40.734497	ef95e3e3-77e3-4577-839c-5b060d3c7d6a	9bbc81b5-e7f3-4bce-9636-d2b45274bd6d
f	0	10	420	2026-06-10 14:30:40.734497	7e12f6fc-bd61-4075-bf53-351cef2cfea3	e1124468-d696-451a-a0a9-4d576dab38af
f	0	3	126	2026-06-10 14:30:40.734497	a5a4bd35-6871-4273-833e-8174106fd02f	9b9327c5-599f-4b7f-9d9b-cfeb92735c48
f	0	15	150	2026-06-10 14:30:40.734497	fd2486c9-a7f1-4467-8fd0-866ffebf5a9e	43792c80-446b-48c5-b107-4a8fc91268df
f	0	1	42	2026-06-10 14:30:40.734497	d994f265-ef9f-4709-882b-92b45af78edd	21f5e577-dded-45a7-bd8a-93f84da36836
f	0	6	60	2026-06-10 14:30:40.734497	c70d50ad-8636-4db6-9b6f-65cda82b5463	3768a7cc-c7fd-499e-8911-bf90ecb516ea
f	0	4	168	2026-06-10 14:30:40.734497	1e20455e-89bf-4451-bba4-dcce5a6a3f75	ff42030d-7e22-49a9-b827-bbce60ecb522
f	0	2	20	2026-06-10 18:11:14.319465	d8f6af15-5d99-45b0-af5a-1f67838163ab	c78b7f7d-11a2-45a9-ad15-e177df66b97f
f	0	2	20	2026-06-10 18:11:14.319465	34b7cebe-6490-4209-a930-8223733f8b37	d6cb115a-202c-4675-919c-11106bfbcc23
f	0	2	20	2026-06-11 18:15:20.140544	502811ed-a28d-463c-8b02-4aac80642ec1	ff086cc2-fc46-4ae5-8cb7-036df9b4ac21
t	9	3	84	2026-06-11 18:49:15.709047	0f2d49df-f36d-4edc-bbe0-df87aced3478	f4eb6e9a-c2ad-4279-9caa-d26d6907f5e8
t	24	16	504	2026-06-11 18:53:45.030441	092477df-1f26-4392-9691-c124db9357a6	4cb63981-7b93-4c8b-b70b-2a4938d34c7f
\.


--
-- Data for Name: stock_adjustment_logs; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.stock_adjustment_logs (id, adjusted_by, batch_id, batch_number, new_secondary_remaining, old_secondary_remaining, product_name, reason, "timestamp") FROM stdin;
d1a12409-82fd-42c2-9b41-d8b91284ecee	Admin	3b6de5e8-7251-4e44-a50e-ebd988b45cfd	TKM260601	760	1596	Takatak Masala	Check [Quantity updated from 79 BOX to 38 BOX]	2026-06-08 00:37:44.035808
cef0c91e-b292-4dff-b7db-2f72f215da7f	Mahmood Azam	18e98954-cee1-480f-a5d1-ea910f101dc5	DAFE22	20	40	All in one-70/ Rs-20	INVALID INPUT [Quantity updated from 4 BOX to 2 BOX]	2026-06-10 12:44:37.26847
1929e265-9abf-4f22-a7eb-80522fa04842	Mahmood Azam	9af177c1-128c-4ad8-8b37-fa03a4cf2144	PAFE27	84	84	Pinut Salted-16Gm/Rs-5	wrong price now corrected [Buy Price updated from ₹1659.76 to ₹1885.36]	2026-06-11 18:12:11.37567
5f3e8392-f875-4434-9199-dfa858fbd2a9	Mahmood Azam	33538e06-f119-4f8c-a039-2e7304775a87	RBFE26	84	84	Pinut Salted-16Gm/Rs-5	now corrected [Buy Price updated from ₹1454.07 to ₹1885.36]	2026-06-11 18:12:56.435458
0f5af30b-9511-46a1-b312-60a96bd006bc	Mahmood Azam	0198c434-d433-446f-b489-b2e2c78ee4f7	RBFE27	20	200	Nut Cracker-70Gm/Rs-20	quantity correction [Quantity updated from 20 BOX to 2 BOX]	2026-06-11 18:15:18.926896
\.


--
-- Data for Name: stock_batches; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.stock_batches (buy_price_with_tax, buy_price_without_tax, expiry_date, gst_percent, is_exhausted, primary_received, secondary_received, secondary_remaining, received_at, id, product_id, batch_number, supplier_name, secondary_soft_reserved) FROM stdin;
852.17	811.59	2026-08-30	5.00	f	38	1596	760	2026-06-08 00:35:12.186021	3b6de5e8-7251-4e44-a50e-ebd988b45cfd	f4c27037-19dd-46ef-9218-c57c23c36c09	TKM260601	Saurabh Agency	0
1526.77	1454.07	2026-10-19	5.00	f	2	20	20	2026-06-10 14:30:40.734497	57beaf28-3175-4150-ae21-2057e28f7e8b	c78b7f7d-11a2-45a9-ad15-e177df66b97f	DAFE22	M/S LARI TRADERS	\N
1526.77	1454.07	2026-09-27	5.00	f	2	20	20	2026-06-10 14:30:40.734497	513c45e1-942b-42c2-9081-713b35c0915a	d6cb115a-202c-4675-919c-11106bfbcc23	MAFE30	M/S LARI TRADERS	\N
1526.77	1454.07	2026-09-21	5.00	f	3	126	126	2026-06-10 14:30:40.734497	b5ae84ae-b412-4603-9726-521e66035543	c8f57c3a-8a0c-471c-9997-505eefa4d11b	MAFE24	M/S LARI TRADERS	\N
1620.29	1543.13	2026-09-28	5.00	f	10	420	420	2026-06-10 14:30:40.734497	996ea546-2497-4808-b60f-2521a2411059	854cae54-9590-4795-87a8-7faef48e2f82	HAFE31A	M/S LARI TRADERS	\N
1526.77	1454.07	2026-09-25	5.00	f	6	252	252	2026-06-10 14:30:40.734497	2d46be23-fe3a-404c-928c-46d13d00a125	45c4235e-0e6d-43fb-9576-3266fd7e1049	HAFE23B	M/S LARI TRADERS	\N
2025.36	1928.91	2026-08-21	5.00	f	10	420	420	2026-06-10 14:30:40.734497	bf7966c1-c7c5-4c1f-97da-b2174d853d47	4cb63981-7b93-4c8b-b70b-2a4938d34c7f	DAFE23	M/S LARI TRADERS	\N
2025.36	1928.91	2026-08-21	5.00	f	2	84	84	2026-06-10 14:30:40.734497	f40a869b-9a75-4d24-975d-6e9336554afa	f4eb6e9a-c2ad-4279-9caa-d26d6907f5e8	DAFE27	M/S LARI TRADERS	\N
2025.36	1928.91	2026-08-21	5.00	f	2	84	84	2026-06-10 14:30:40.734497	bc9d425a-c7f3-4e5b-b7a6-6f23676faa37	4cb63981-7b93-4c8b-b70b-2a4938d34c7f	PBFD23	M/S LARI TRADERS	\N
1224.64	1166.32	2026-09-21	5.00	f	3	30	30	2026-06-10 14:30:40.734497	08861994-aa85-4b3b-9298-ddb71a6c3425	9bbc81b5-e7f3-4bce-9636-d2b45274bd6d	PBFE24	M/S LARI TRADERS	\N
1460.14	1390.61	2026-09-16	5.00	f	10	420	420	2026-06-10 14:30:40.734497	e3c7dab7-dd22-4f1b-9d51-d42e5ad64415	e1124468-d696-451a-a0a9-4d576dab38af	HAFE19A	M/S LARI TRADERS	\N
1526.77	1454.07	2026-09-25	5.00	f	3	126	126	2026-06-10 14:30:40.734497	f2481b63-f5f3-4d7e-bcf6-6cf3ad3aadfb	9b9327c5-599f-4b7f-9d9b-cfeb92735c48	HAFE28B	M/S LARI TRADERS	\N
2025.36	1928.91	2026-09-20	5.00	f	15	150	150	2026-06-10 14:30:40.734497	d2334016-93b6-4995-89c6-b64fc0ca458a	43792c80-446b-48c5-b107-4a8fc91268df	DAFE23	M/S LARI TRADERS	\N
2025.36	1928.91	2026-09-19	5.00	f	1	42	42	2026-06-10 14:30:40.734497	e65d4644-52f8-4bcc-b449-99eed5370443	21f5e577-dded-45a7-bd8a-93f84da36836	DAFE22	M/S LARI TRADERS	\N
2025.36	1928.91	2026-09-19	5.00	f	6	60	60	2026-06-10 14:30:40.734497	d9ebc2cd-4d69-47c4-9d0b-7cb5f5d8cc02	3768a7cc-c7fd-499e-8911-bf90ecb516ea	MAFE22	M/S LARI TRADERS	\N
1979.63	1885.36	2026-09-24	5.00	f	2	84	84	2026-06-10 14:30:40.734497	9af177c1-128c-4ad8-8b37-fa03a4cf2144	ff42030d-7e22-49a9-b827-bbce60ecb522	PAFE27	M/S LARI TRADERS	\N
1979.63	1885.36	2026-09-23	5.00	f	2	84	84	2026-06-10 14:30:40.734497	33538e06-f119-4f8c-a039-2e7304775a87	ff42030d-7e22-49a9-b827-bbce60ecb522	RBFE26	M/S LARI TRADERS	\N
1526.77	1454.07	2026-09-24	5.00	f	2	200	20	2026-06-10 14:30:40.734497	0198c434-d433-446f-b489-b2e2c78ee4f7	ff086cc2-fc46-4ae5-8cb7-036df9b4ac21	RBFE27	M/S LARI TRADERS	\N
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.users (is_active, must_change_password, created_at, updated_at, id, name, password_hash, phone, role, last_latitude, last_location_time, last_longitude, monthly_salary) FROM stdin;
t	t	2026-06-07 22:22:20.667877	2026-06-07 22:22:20.667877	dd395f46-df8b-4eec-95f8-2f8c345ec0cd	Mahfooz Alam	$2a$10$BVciEocwRCeOLm6FT48he.bEAwnbrKEWGJKJ8sshYPMYd19Z8q20q	8707867084	MANAGER	\N	\N	\N	\N
t	f	2026-06-07 22:21:39.623228	2026-06-07 22:23:39.712516	a4305e90-7096-4fdf-9262-12b672d8ca41	Mahmood Azam	$2a$10$F/7LKp5xT2Yn9vEqYagRfeXUk7He6XA/jfDTaX7t41chuEjTZyZKG	7011752116	ADMIN	\N	\N	\N	\N
t	t	2026-06-09 00:53:27.503396	2026-06-09 00:53:27.503396	35a9ce32-7435-4bc3-8bff-0131d8bff791	Anas	$2a$10$BkWXvRxHLgaJzU84f7lA6u4WZ5tVs.EWeBaNwNzYuItfVXlkfl8iW	6307539142	SALESMAN	\N	\N	\N	\N
t	f	2026-06-07 19:52:17.047451	2026-06-11 17:28:23.888799	bf9f7585-b5bb-4ffb-a664-6e8a94e8d980	Mashkoor Alam	$2a$10$8T3iLj6vHPyGHVleXzxE9.L4.BSstBZf16ievvJzeNiAAL.uYaGLq	7084285785	ADMIN	\N	\N	\N	\N
t	f	2026-06-11 19:14:10.050522	2026-06-11 19:14:10.050522	45f1ceba-db94-412f-8102-830a5c342b4e	Admin	$2a$10$svklynNQ8o6uavZ7.6d.hOA5szWIqFyYAxW4SM7Lc0mVpnPH7FJtq	9999999999	ADMIN	\N	\N	\N	\N
\.


--
-- Name: areas areas_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.areas
    ADD CONSTRAINT areas_name_key UNIQUE (name);


--
-- Name: areas areas_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.areas
    ADD CONSTRAINT areas_pkey PRIMARY KEY (id);


--
-- Name: bill_items bill_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_items
    ADD CONSTRAINT bill_items_pkey PRIMARY KEY (id);


--
-- Name: bills bills_bill_number_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_bill_number_key UNIQUE (bill_number);


--
-- Name: bills bills_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_pkey PRIMARY KEY (id);


--
-- Name: customers customers_customer_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_customer_code_key UNIQUE (customer_code);


--
-- Name: customers customers_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT customers_pkey PRIMARY KEY (id);


--
-- Name: damage_log damage_log_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.damage_log
    ADD CONSTRAINT damage_log_pkey PRIMARY KEY (id);


--
-- Name: deliveries deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliveries
    ADD CONSTRAINT deliveries_pkey PRIMARY KEY (id);


--
-- Name: expenses expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);


--
-- Name: payments payments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT payments_pkey PRIMARY KEY (id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: products products_product_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_product_code_key UNIQUE (product_code);


--
-- Name: stock_adjustment_logs stock_adjustment_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_adjustment_logs
    ADD CONSTRAINT stock_adjustment_logs_pkey PRIMARY KEY (id);


--
-- Name: stock_batches stock_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_batches
    ADD CONSTRAINT stock_batches_pkey PRIMARY KEY (id);


--
-- Name: stock stock_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT stock_pkey PRIMARY KEY (id);


--
-- Name: stock stock_product_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT stock_product_id_key UNIQUE (product_id);


--
-- Name: users users_phone_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_phone_key UNIQUE (phone);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: idx_bill_created_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_created_at ON public.bills USING btree (created_at DESC);


--
-- Name: idx_bill_customer_created_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_bill_customer_created_at ON public.bills USING btree (customer_id, created_at DESC);


--
-- Name: idx_customer_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_customer_active ON public.customers USING btree (is_active);


--
-- Name: idx_customer_area; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_customer_area ON public.customers USING btree (area_id);


--
-- Name: idx_customer_last_order; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_customer_last_order ON public.customers USING btree (last_order_at DESC);


--
-- Name: idx_customer_phone; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_customer_phone ON public.customers USING btree (phone);


--
-- Name: idx_delivery_boy_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_delivery_boy_status ON public.deliveries USING btree (delivery_boy_id, status);


--
-- Name: idx_delivery_created_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_delivery_created_at ON public.deliveries USING btree (created_at DESC);


--
-- Name: idx_payment_bill_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_bill_id ON public.payments USING btree (bill_id);


--
-- Name: idx_payment_collected_by_paid_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_collected_by_paid_at ON public.payments USING btree (collected_by, paid_at DESC);


--
-- Name: idx_payment_customer_paid_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_customer_paid_at ON public.payments USING btree (customer_id, paid_at DESC);


--
-- Name: idx_payment_paid_at; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_payment_paid_at ON public.payments USING btree (paid_at DESC);


--
-- Name: expenses fk1v3fm4qkqe12jbr7mxpn67uqe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT fk1v3fm4qkqe12jbr7mxpn67uqe FOREIGN KEY (recipient_id) REFERENCES public.users(id);


--
-- Name: damage_log fk2oilogdteb8y5gxgghqw76236; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.damage_log
    ADD CONSTRAINT fk2oilogdteb8y5gxgghqw76236 FOREIGN KEY (batch_id) REFERENCES public.stock_batches(id);


--
-- Name: payments fk45dp0030s8e3myd8n6ky4e79g; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk45dp0030s8e3myd8n6ky4e79g FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: payments fk9565r6579khpdjxnyla0l2ycd; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fk9565r6579khpdjxnyla0l2ycd FOREIGN KEY (bill_id) REFERENCES public.bills(id);


--
-- Name: damage_log fkby488il73y40tcf975mehahyp; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.damage_log
    ADD CONSTRAINT fkby488il73y40tcf975mehahyp FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: stock_batches fkde4mxi94h28dwxdtr6lg8umfs; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock_batches
    ADD CONSTRAINT fkde4mxi94h28dwxdtr6lg8umfs FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: deliveries fkeci9rr5xkfprha3aj8tj73f6u; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliveries
    ADD CONSTRAINT fkeci9rr5xkfprha3aj8tj73f6u FOREIGN KEY (delivery_boy_id) REFERENCES public.users(id);


--
-- Name: stock fkeuiihog7wq4cu7nvqu7jx57d2; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT fkeuiihog7wq4cu7nvqu7jx57d2 FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: deliveries fkfsvruixg0c950pjc3jvqotwup; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.deliveries
    ADD CONSTRAINT fkfsvruixg0c950pjc3jvqotwup FOREIGN KEY (bill_id) REFERENCES public.bills(id);


--
-- Name: bill_items fkgu3ilc0js2i5rk7xe8gcnxa15; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_items
    ADD CONSTRAINT fkgu3ilc0js2i5rk7xe8gcnxa15 FOREIGN KEY (batch_id) REFERENCES public.stock_batches(id);


--
-- Name: areas fkin2my25wimc8x34fx670mdpte; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.areas
    ADD CONSTRAINT fkin2my25wimc8x34fx670mdpte FOREIGN KEY (salesman_id) REFERENCES public.users(id);


--
-- Name: bill_items fkj9o7g8krc56gf6t6f0sy4ic5p; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_items
    ADD CONSTRAINT fkj9o7g8krc56gf6t6f0sy4ic5p FOREIGN KEY (bill_id) REFERENCES public.bills(id);


--
-- Name: customers fkjpme7lg1i9l8vxh9n9irl49ip; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.customers
    ADD CONSTRAINT fkjpme7lg1i9l8vxh9n9irl49ip FOREIGN KEY (area_id) REFERENCES public.areas(id);


--
-- Name: expenses fkk7nnwvg2pr7cl4gl6rqic0b1a; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT fkk7nnwvg2pr7cl4gl6rqic0b1a FOREIGN KEY (added_by) REFERENCES public.users(id);


--
-- Name: damage_log fklf3l2dwi9nfbk2w3y3763bjq; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.damage_log
    ADD CONSTRAINT fklf3l2dwi9nfbk2w3y3763bjq FOREIGN KEY (logged_by) REFERENCES public.users(id);


--
-- Name: payments fkll6si0jovk9ppl7qwyikpvnua; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.payments
    ADD CONSTRAINT fkll6si0jovk9ppl7qwyikpvnua FOREIGN KEY (collected_by) REFERENCES public.users(id);


--
-- Name: bill_items fknxfjfage047r297vj65sq8e6h; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bill_items
    ADD CONSTRAINT fknxfjfage047r297vj65sq8e6h FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- Name: bills fkoy9sc2dmxj2qwjeiiilf3yuxp; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fkoy9sc2dmxj2qwjeiiilf3yuxp FOREIGN KEY (customer_id) REFERENCES public.customers(id);


--
-- Name: bills fky1a54ui2dn4jbg16u5xhypg2; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fky1a54ui2dn4jbg16u5xhypg2 FOREIGN KEY (created_by) REFERENCES public.users(id);


--
-- PostgreSQL database dump complete
--

\unrestrict GIl9rMsnj1zMin7raFuvme2FYy8GSqYEfWxINr7VcbNlhV4P4NknxS3WfwUlgec

