-- Initial dishes converted from dishes-seed.json.

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Bandeja Paisa', 'El plato más emblemático de la región antioqueña. Una generosa bandeja con frijoles rojos, arroz blanco, chicharrón crujiente, carne molida, chorizo, huevo frito, plátano maduro, arepa y aguacate.', 'https://images.unsplash.com/photo-1599655722738-ae7a40b0b994?w=600', 'https://images.unsplash.com/photo-1599655722738-ae7a40b0b994?w=200', 28000, 85, 55, 4.9, 'Frijoles rojos, arroz blanco, chicharrón, carne molida, chorizo, huevo frito, plátano maduro, arepa, aguacate', true
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Bandeja Paisa');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Ajiaco Santafereño', 'Sopa tradicional bogotana preparada con tres variedades de papa (criolla, pastusa y sabanera), pollo desmechado, mazorca y guascas. Se sirve con alcaparras, crema de leche y aguacate.', 'https://images.unsplash.com/photo-1547592166-23ac45744acd?w=600', 'https://images.unsplash.com/photo-1547592166-23ac45744acd?w=200', 22000, 50, 35, 4.8, 'Papa criolla, papa pastusa, papa sabanera, pollo, mazorca, guascas, alcaparras, crema de leche, aguacate', true
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Ajiaco Santafereño');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Sancocho de Gallina', 'El plato que une a las familias colombianas. Un caldo sustancioso con gallina criolla, yuca, plátano verde, papa, mazorca y cilantro. Típico de los domingos en la Costa Caribe y el Valle del Cauca.', 'https://images.unsplash.com/photo-1603105037880-880cd4edfb0d?w=600', 'https://images.unsplash.com/photo-1603105037880-880cd4edfb0d?w=200', 20000, 55, 40, 4.7, 'Gallina criolla, yuca, plátano verde, papa, mazorca, cilantro, cebollín, ajo, comino', true
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Sancocho de Gallina');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Arepa de Huevo', 'Icono de la gastronomía costeña, especialmente de Luruaco y Cartagena. Arepa de maíz frita rellena de huevo, crujiente por fuera y suave por dentro. Se acompaña con suero costeño.', 'https://images.unsplash.com/photo-1604329760661-e71dc83f8f26?w=600', 'https://images.unsplash.com/photo-1604329760661-e71dc83f8f26?w=200', 5000, 35, 12, 4.6, 'Maíz amarillo, huevo, sal, aceite, suero costeño', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Arepa de Huevo');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Lechona Tolimense', 'Tradición del Tolima Grande. Cerdo entero relleno de arroz, arvejas amarillas y especias, horneado lentamente durante horas. Se sirve con arepa delgada.', 'https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=600', 'https://images.unsplash.com/photo-1529692236671-f1f6cf9683ba?w=200', 18000, 45, 50, 4.7, 'Cerdo, arroz, arvejas amarillas, cebolla larga, ajo, comino, pimienta, arepa', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Lechona Tolimense');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Mojarra Frita', 'Pescado entero frito hasta quedar dorado y crujiente. Un clásico de los ríos y costas colombianas. Se sirve con arroz de coco, patacones y ensalada.', 'https://images.unsplash.com/photo-1534604973900-c43ab4c2e0ab?w=600', 'https://images.unsplash.com/photo-1534604973900-c43ab4c2e0ab?w=200', 25000, 40, 45, 4.5, 'Mojarra, arroz de coco, plátano verde (patacones), limón, ensalada, ajo', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Mojarra Frita');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Empanadas Colombianas', 'Crujientes empanadas de masa de maíz rellenas de carne molida sazonada con hogao, papa y comino. Se acompañan con ají colombiano casero.', 'https://images.unsplash.com/photo-1614735241165-6756e1df61ab?w=600', 'https://images.unsplash.com/photo-1614735241165-6756e1df61ab?w=200', 3000, 30, 15, 4.6, 'Maíz, carne molida, papa, hogao (tomate, cebolla), comino, ají', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Empanadas Colombianas');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Cazuela de Frijoles', 'Frijoles rojos cocinados a fuego lento con garra de cerdo, plátano maduro y chicharrón desmenuzado. Se sirve con arroz blanco y aguacate. Muy popular en Antioquia y el Eje Cafetero.', 'https://images.unsplash.com/photo-1574894709920-11b28e7367e3?w=600', 'https://images.unsplash.com/photo-1574894709920-11b28e7367e3?w=200', 16000, 55, 30, 4.4, 'Frijoles rojos, garra de cerdo, plátano maduro, chicharrón, zanahoria, hogao, arroz blanco, aguacate', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Cazuela de Frijoles');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Tamal Colombiano', 'Masa de maíz rellena de pollo, cerdo, zanahoria, arvejas, huevo y arroz, envuelta en hojas de plátano y cocida al vapor. Cada región tiene su propia versión.', 'https://images.unsplash.com/photo-1586511925558-a4c6376fe65f?w=600', 'https://images.unsplash.com/photo-1586511925558-a4c6376fe65f?w=200', 8000, 50, 25, 4.5, 'Masa de maíz, pollo, cerdo, zanahoria, arvejas, huevo duro, arroz, hojas de plátano', true
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Tamal Colombiano');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Ceviche de Camarón Cartagenero', 'Camarones frescos marinados en limón con cebolla morada, tomate, cilantro y un toque de salsa rosada. Servido con galletas de soda. Muy popular en la Costa Caribe.', 'https://images.unsplash.com/photo-1535399831218-d5bd36d1a6b3?w=600', 'https://images.unsplash.com/photo-1535399831218-d5bd36d1a6b3?w=200', 20000, 15, 28, 4.6, 'Camarón, limón, cebolla morada, tomate, cilantro, salsa rosada, salsa negra, galletas de soda', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Ceviche de Camarón Cartagenero');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Arroz Atollado Valluno', 'Arroz cremoso y caldoso típico del Valle del Cauca, preparado con costilla de cerdo, pollo, longaniza, papa, hogao y aliños criollos.', 'https://images.unsplash.com/photo-1516714435131-44d6b64dc6a2?w=600', 'https://images.unsplash.com/photo-1516714435131-44d6b64dc6a2?w=200', 19000, 60, 35, 4.5, 'Arroz, costilla de cerdo, pollo, longaniza, papa criolla, hogao, cilantro, comino, color', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Arroz Atollado Valluno');

INSERT INTO dishes (id, name, description, image, thumbnail, price, carbohydrates, proteins, rating, ingredients, flag_header)
SELECT gen_random_uuid(), 'Changua', 'Caldo boyacense de desayuno preparado con leche, agua, huevos escalfados, cebolla larga y cilantro. Se sirve con pan duro o calado. Reconfortante en las mañanas frías del altiplano.', 'https://images.unsplash.com/photo-1476718406336-bb5a9690ee2a?w=600', 'https://images.unsplash.com/photo-1476718406336-bb5a9690ee2a?w=200', 8000, 20, 15, 4.2, 'Leche, agua, huevos, cebolla larga, cilantro, pan calado, sal', false
WHERE NOT EXISTS (SELECT 1 FROM dishes WHERE name = 'Changua');

