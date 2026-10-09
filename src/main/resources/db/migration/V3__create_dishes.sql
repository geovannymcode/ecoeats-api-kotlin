CREATE TABLE dishes (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    image VARCHAR(500) NOT NULL DEFAULT '',
    thumbnail VARCHAR(500) NOT NULL DEFAULT '',
    price NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
    carbohydrates INT NOT NULL DEFAULT 0,
    proteins INT NOT NULL DEFAULT 0,
    rating NUMERIC(3,1) NOT NULL DEFAULT 0 CHECK (rating BETWEEN 0 AND 5),
    ingredients TEXT NOT NULL DEFAULT '',
    flag_header BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX dishes_name_idx ON dishes (name);
