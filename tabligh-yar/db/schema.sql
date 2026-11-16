CREATE TABLE IF NOT EXISTS app_settings (
  id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  price_per_10000 BIGINT NOT NULL DEFAULT 90000 CHECK (price_per_10000 > 0),
  min_views INTEGER NOT NULL DEFAULT 1000,
  max_views INTEGER NOT NULL DEFAULT 200000,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
INSERT INTO app_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS admins (
  id BIGSERIAL PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ad_orders (
  id BIGSERIAL PRIMARY KEY,
  user_id TEXT NOT NULL,
  user_name TEXT,
  channel_url TEXT NOT NULL,
  ad_text TEXT NOT NULL,
  requested_views INTEGER NOT NULL,
  quoted_price BIGINT NOT NULL,
  price_per_10000_snapshot BIGINT NOT NULL,
  receipt_path TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending','paid','payment_rejected','published','completed','cancelled')),
  admin_note TEXT,
  published_post_url TEXT,
  final_views INTEGER,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS order_events (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES ad_orders(id) ON DELETE CASCADE,
  admin_username TEXT NOT NULL,
  old_status TEXT,
  new_status TEXT NOT NULL,
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ad_orders_user_created_idx ON ad_orders(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ad_orders_status_created_idx ON ad_orders(status, created_at DESC);