drop table if exists audit_log;
drop table if exists agent_trace;
drop table if exists after_sale;
drop table if exists logistics;
drop table if exists orders;
drop table if exists knowledge_document;
drop table if exists app_user;

create table app_user (
  id bigint primary key auto_increment,
  username varchar(64) not null unique,
  password_hash varchar(100) not null,
  display_name varchar(64) not null,
  role varchar(32) not null
);

create table orders (
  id bigint primary key auto_increment,
  order_no varchar(32) not null unique,
  user_id bigint not null,
  product_name varchar(128) not null,
  amount decimal(12,2) not null,
  status varchar(32) not null,
  paid_at timestamp not null,
  version int not null default 0,
  constraint fk_order_user foreign key (user_id) references app_user(id)
);

create index idx_order_user_paid on orders(user_id, paid_at);

create table logistics (
  id bigint primary key auto_increment,
  order_id bigint not null unique,
  carrier varchar(64) not null,
  tracking_no varchar(64) not null,
  current_status varchar(64) not null,
  latest_event varchar(255) not null,
  updated_at timestamp not null,
  constraint fk_logistics_order foreign key (order_id) references orders(id)
);

create table after_sale (
  id bigint primary key auto_increment,
  request_no varchar(40) not null unique,
  order_id bigint not null,
  user_id bigint not null,
  reason varchar(255) not null,
  status varchar(32) not null,
  idempotency_key varchar(80) not null unique,
  created_at timestamp not null,
  constraint fk_after_sale_order foreign key (order_id) references orders(id),
  constraint fk_after_sale_user foreign key (user_id) references app_user(id)
);

create table knowledge_document (
  id bigint primary key auto_increment,
  title varchar(128) not null,
  category varchar(64) not null,
  content varchar(2000) not null
);

create table agent_trace (
  id bigint primary key auto_increment,
  trace_id varchar(40) not null unique,
  user_id bigint not null,
  intent varchar(40) not null,
  status varchar(40) not null,
  tool_calls int not null,
  retrieval_docs int not null,
  latency_ms bigint not null,
  created_at timestamp not null
);

create table audit_log (
  id bigint primary key auto_increment,
  trace_id varchar(40) not null,
  user_id bigint not null,
  action varchar(64) not null,
  resource_id varchar(64),
  result varchar(32) not null,
  detail varchar(500),
  created_at timestamp not null
);

create index idx_audit_user_created on audit_log(user_id, created_at);
