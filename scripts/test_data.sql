-- =============================================================================
-- Adopt-U Test Data
-- 50 users (mixed roles) + 200 pets (mainly dogs)
-- Password for all users: Test1234!
-- =============================================================================

-- -------------------------
-- CLEANUP: remove existing test data so re-runs work cleanly.
-- Test users are identified by their well-known email addresses OR by
-- explicit IDs 1-50 left from a previous run of this script.
-- Deletion order follows FK dependency (deepest dependents first).
-- -------------------------
DO $$
DECLARE
  v_user_ids  INTEGER[];
  v_pet_ids   INTEGER[];
BEGIN
  SELECT ARRAY(
    SELECT DISTINCT id FROM users
    WHERE username = ANY(ARRAY[
      'admin@adoptu.com',
      'maria.garcia@email.com',       'carlos.rodriguez@email.com',
      'ana.martinez@email.com',        'luis.hernandez@email.com',
      'sofia.lopez@email.com',         'pedro.gonzalez@email.com',
      'valentina.perez@email.com',     'diego.sanchez@email.com',
      'isabela.ramirez@email.com',     'mateo.flores@email.com',
      'camila.torres@email.com',       'alejandro.jimenez@email.com',
      'luciana.morales@email.com',     'sebastian.vargas@email.com',
      'valeria.castillo@email.com',
      'juan.medina@email.com',         'daniela.nunez@email.com',
      'roberto.delgado@email.com',     'mariana.silva@email.com',
      'pablo.ramos@email.com',         'andrea.romero@email.com',
      'miguel.garcia@email.com',       'natalia.rodriguez@email.com',
      'francisco.martinez@email.com',  'carolina.hernandez@email.com',
      'jorge.photo@email.com',         'laura.photo@email.com',
      'andres.photo@email.com',        'monica.photo@email.com',
      'raul.photo@email.com',
      'elena.foster@email.com',        'thomas.foster@email.com',
      'lucia.casa@email.com',          'martin.temporal@email.com',
      'rosa.home@email.com',           'felix.hogar@email.com',
      'catalina.casa@email.com',       'hugo.temporal@email.com',
      'shelter.amigos@email.com',      'shelter.esperanza@email.com',
      'shelter.patitas@email.com',     'shelter.huellitas@email.com',
      'shelter.vida@email.com',
      'steril.clinic1@email.com',      'steril.clinic2@email.com',
      'steril.clinic3@email.com',
      'rescuer.photo1@email.com',      'rescuer.home1@email.com',
      'rescuer.adopter1@email.com'
    ])
    OR id BETWEEN 1 AND 50
  ) INTO v_user_ids;

  IF array_length(v_user_ids, 1) IS NULL THEN
    RETURN;
  END IF;

  SELECT ARRAY(SELECT id FROM pets WHERE rescuer_id = ANY(v_user_ids))
  INTO v_pet_ids;

  -- Leaf tables first (reference both pets and users)
  IF array_length(v_pet_ids, 1) IS NOT NULL THEN
    DELETE FROM adoption_requests
      WHERE pet_id = ANY(v_pet_ids)
         OR adopter_id = ANY(v_user_ids);
    DELETE FROM photography_requests
      WHERE pet_id = ANY(v_pet_ids)
         OR photographer_id = ANY(v_user_ids)
         OR requester_id    = ANY(v_user_ids);
    DELETE FROM temporal_home_requests
      WHERE pet_id         = ANY(v_pet_ids)
         OR temporal_home_id = ANY(v_user_ids)
         OR rescuer_id       = ANY(v_user_ids);
  ELSE
    DELETE FROM adoption_requests
      WHERE adopter_id = ANY(v_user_ids);
    DELETE FROM photography_requests
      WHERE photographer_id = ANY(v_user_ids)
         OR requester_id    = ANY(v_user_ids);
    DELETE FROM temporal_home_requests
      WHERE temporal_home_id = ANY(v_user_ids)
         OR rescuer_id       = ANY(v_user_ids);
  END IF;

  DELETE FROM blocked_rescuers
    WHERE temporal_home_id = ANY(v_user_ids)
       OR rescuer_id       = ANY(v_user_ids);

  -- Role-specific profile tables
  DELETE FROM pets                         WHERE rescuer_id = ANY(v_user_ids);
  DELETE FROM photographers                WHERE user_id    = ANY(v_user_ids);
  DELETE FROM temporal_homes               WHERE user_id    = ANY(v_user_ids);
  DELETE FROM user_shelters                WHERE user_id    = ANY(v_user_ids);
  DELETE FROM user_sterilization_locations WHERE user_id    = ANY(v_user_ids);

  -- Auth / session tables
  DELETE FROM user_active_roles            WHERE user_id = ANY(v_user_ids);
  DELETE FROM user_passwords               WHERE user_id = ANY(v_user_ids);
  DELETE FROM email_verification_tokens    WHERE user_id = ANY(v_user_ids);
  DELETE FROM magic_link_tokens            WHERE user_id = ANY(v_user_ids);
  DELETE FROM password_reset_tokens        WHERE user_id = ANY(v_user_ids);
  DELETE FROM email_change_tokens          WHERE user_id = ANY(v_user_ids);
  DELETE FROM email_verification_attempts  WHERE user_id = ANY(v_user_ids);
  DELETE FROM webauthn_credentials         WHERE user_id = ANY(v_user_ids);

  DELETE FROM users WHERE id = ANY(v_user_ids);
END $$;

-- -------------------------
-- USERS
-- -------------------------
INSERT INTO users (id, username, display_name, language, created_at, last_accepted_privacy_policy, last_accepted_terms_and_conditions, is_email_verified, is_banned)
OVERRIDING SYSTEM VALUE
VALUES
  -- Admin
  (1,  'admin@adoptu.com',               'Admin Adoptu',          'es', 1704067200000, 1704067200000, 1704067200000, true,  false),
  -- Rescuers (2-16)
  (2,  'maria.garcia@email.com',         'María García',          'es', 1704153600000, 1704153600000, 1704153600000, true,  false),
  (3,  'carlos.rodriguez@email.com',     'Carlos Rodríguez',      'es', 1706745600000, 1706745600000, 1706745600000, true,  false),
  (4,  'ana.martinez@email.com',         'Ana Martínez',          'es', 1709424000000, 1709424000000, 1709424000000, true,  false),
  (5,  'luis.hernandez@email.com',       'Luis Hernández',        'es', 1712102400000, 1712102400000, 1712102400000, true,  false),
  (6,  'sofia.lopez@email.com',          'Sofía López',           'es', 1714694400000, 1714694400000, 1714694400000, true,  false),
  (7,  'pedro.gonzalez@email.com',       'Pedro González',        'es', 1717372800000, 1717372800000, 1717372800000, true,  false),
  (8,  'valentina.perez@email.com',      'Valentina Pérez',       'es', 1719964800000, 1719964800000, 1719964800000, true,  false),
  (9,  'diego.sanchez@email.com',        'Diego Sánchez',         'es', 1722643200000, 1722643200000, 1722643200000, true,  false),
  (10, 'isabela.ramirez@email.com',      'Isabela Ramírez',       'es', 1725321600000, 1725321600000, 1725321600000, true,  false),
  (11, 'mateo.flores@email.com',         'Mateo Flores',          'es', 1727913600000, 1727913600000, 1727913600000, true,  false),
  (12, 'camila.torres@email.com',        'Camila Torres',         'es', 1730592000000, 1730592000000, 1730592000000, true,  false),
  (13, 'alejandro.jimenez@email.com',    'Alejandro Jiménez',     'es', 1733184000000, 1733184000000, 1733184000000, true,  false),
  (14, 'luciana.morales@email.com',      'Luciana Morales',       'es', 1735862400000, 1735862400000, 1735862400000, true,  false),
  (15, 'sebastian.vargas@email.com',     'Sebastián Vargas',      'es', 1738540800000, 1738540800000, 1738540800000, true,  false),
  (16, 'valeria.castillo@email.com',     'Valeria Castillo',      'es', 1741132800000, 1741132800000, 1741132800000, true,  false),
  -- Adopters (17-26)
  (17, 'juan.medina@email.com',          'Juan Medina',           'es', 1704240000000, 1704240000000, 1704240000000, true,  false),
  (18, 'daniela.nunez@email.com',        'Daniela Núñez',         'es', 1707004800000, 1707004800000, 1707004800000, true,  false),
  (19, 'roberto.delgado@email.com',      'Roberto Delgado',       'es', 1709596800000, 1709596800000, 1709596800000, true,  false),
  (20, 'mariana.silva@email.com',        'Mariana Silva',         'es', 1712275200000, 1712275200000, 1712275200000, true,  false),
  (21, 'pablo.ramos@email.com',          'Pablo Ramos',           'es', 1714867200000, 1714867200000, 1714867200000, true,  false),
  (22, 'andrea.romero@email.com',        'Andrea Romero',         'es', 1717545600000, 1717545600000, 1717545600000, true,  false),
  (23, 'miguel.garcia@email.com',        'Miguel García',         'es', 1720224000000, 1720224000000, 1720224000000, true,  false),
  (24, 'natalia.rodriguez@email.com',    'Natalia Rodríguez',     'es', 1722816000000, 1722816000000, 1722816000000, true,  false),
  (25, 'francisco.martinez@email.com',   'Francisco Martínez',    'es', 1725494400000, 1725494400000, 1725494400000, true,  false),
  (26, 'carolina.hernandez@email.com',   'Carolina Hernández',    'es', 1728086400000, 1728086400000, 1728086400000, true,  false),
  -- Photographers (27-31)
  (27, 'jorge.photo@email.com',          'Jorge Fotógrafo',       'es', 1704326400000, 1704326400000, 1704326400000, true,  false),
  (28, 'laura.photo@email.com',          'Laura Fotografía',      'es', 1707091200000, 1707091200000, 1707091200000, true,  false),
  (29, 'andres.photo@email.com',         'Andrés Foto',           'es', 1709769600000, 1709769600000, 1709769600000, true,  false),
  (30, 'monica.photo@email.com',         'Mónica Photo',          'es', 1712448000000, 1712448000000, 1712448000000, true,  false),
  (31, 'raul.photo@email.com',           'Raúl Fotógrafo',        'es', 1715040000000, 1715040000000, 1715040000000, true,  false),
  -- Temporal homes (32-39)
  (32, 'elena.foster@email.com',         'Elena Foster',          'es', 1704412800000, 1704412800000, 1704412800000, true,  false),
  (33, 'thomas.foster@email.com',        'Tomás Foster',          'es', 1707177600000, 1707177600000, 1707177600000, true,  false),
  (34, 'lucia.casa@email.com',           'Lucía Temporal',        'es', 1709856000000, 1709856000000, 1709856000000, true,  false),
  (35, 'martin.temporal@email.com',      'Martín Temporal',       'es', 1712534400000, 1712534400000, 1712534400000, true,  false),
  (36, 'rosa.home@email.com',            'Rosa Hogar',            'es', 1715126400000, 1715126400000, 1715126400000, true,  false),
  (37, 'felix.hogar@email.com',          'Félix Hogar',           'es', 1717804800000, 1717804800000, 1717804800000, true,  false),
  (38, 'catalina.casa@email.com',        'Catalina Casa',         'es', 1720396800000, 1720396800000, 1720396800000, true,  false),
  (39, 'hugo.temporal@email.com',        'Hugo Temporal',         'es', 1723075200000, 1723075200000, 1723075200000, true,  false),
  -- Shelter managers (40-44)
  (40, 'shelter.amigos@email.com',       'Refugio Amigos Peludos','es', 1704499200000, 1704499200000, 1704499200000, true,  false),
  (41, 'shelter.esperanza@email.com',    'Refugio Esperanza',     'es', 1707264000000, 1707264000000, 1707264000000, true,  false),
  (42, 'shelter.patitas@email.com',      'Refugio Patitas',       'es', 1709942400000, 1709942400000, 1709942400000, true,  false),
  (43, 'shelter.huellitas@email.com',    'Refugio Huellitas',     'es', 1712620800000, 1712620800000, 1712620800000, true,  false),
  (44, 'shelter.vida@email.com',         'Refugio Nueva Vida',    'es', 1715212800000, 1715212800000, 1715212800000, true,  false),
  -- Sterilization service (45-47)
  (45, 'steril.clinic1@email.com',       'Clínica Esteriliza Ya', 'es', 1704585600000, 1704585600000, 1704585600000, true,  false),
  (46, 'steril.clinic2@email.com',       'Clínica Bienestar',     'es', 1707350400000, 1707350400000, 1707350400000, true,  false),
  (47, 'steril.clinic3@email.com',       'Clínica Sana Mascota',  'es', 1710028800000, 1710028800000, 1710028800000, true,  false),
  -- Mixed roles (48-50)
  (48, 'rescuer.photo1@email.com',       'Carmen Rescatadora',    'es', 1712707200000, 1712707200000, 1712707200000, true,  false),
  (49, 'rescuer.home1@email.com',        'Ernesto Multirol',      'es', 1715299200000, 1715299200000, 1715299200000, true,  false),
  (50, 'rescuer.adopter1@email.com',     'Patricia Corazón',      'es', 1717977600000, 1717977600000, 1717977600000, true,  false)
ON CONFLICT (id) DO NOTHING;

-- Advance the sequence past the manually inserted IDs so future inserts don't collide
SELECT setval(pg_get_serial_sequence('users', 'id'), GREATEST((SELECT MAX(id) FROM users), 50), true);

-- -------------------------
-- PASSWORDS (all: Test1234!)
-- -------------------------
INSERT INTO user_passwords (user_id, password_hash, created_at, updated_at)
SELECT id, '$argon2id$v=19$m=65536,t=3,p=4$FrWyc5rTivlqFuys+G+Q6Q$uToUdFF9IBhSYRV+OCHW6IFgnVdmxCR98BBEIa6/NNhNLgg5E5d61vIne7XtHIlzCFl7dnRwsnmNQmKb+eKyFQ', 1704067200000, 1704067200000
FROM users WHERE id BETWEEN 1 AND 50
ON CONFLICT (user_id) DO NOTHING;

-- -------------------------
-- ROLES
-- -------------------------
INSERT INTO user_active_roles (user_id, role) VALUES
  -- Admin (1)
  (1,  'ADMIN'),
  -- Rescuers (2-16)
  (2,  'RESCUER'), (3,  'RESCUER'), (4,  'RESCUER'), (5,  'RESCUER'),
  (6,  'RESCUER'), (7,  'RESCUER'), (8,  'RESCUER'), (9,  'RESCUER'),
  (10, 'RESCUER'), (11, 'RESCUER'), (12, 'RESCUER'), (13, 'RESCUER'),
  (14, 'RESCUER'), (15, 'RESCUER'), (16, 'RESCUER'),
  -- Adopters (17-26)
  (17, 'ADOPTER'), (18, 'ADOPTER'), (19, 'ADOPTER'), (20, 'ADOPTER'),
  (21, 'ADOPTER'), (22, 'ADOPTER'), (23, 'ADOPTER'), (24, 'ADOPTER'),
  (25, 'ADOPTER'), (26, 'ADOPTER'),
  -- Photographers (27-31)
  (27, 'PHOTOGRAPHER'), (28, 'PHOTOGRAPHER'), (29, 'PHOTOGRAPHER'),
  (30, 'PHOTOGRAPHER'), (31, 'PHOTOGRAPHER'),
  -- Temporal homes (32-39)
  (32, 'TEMPORAL_HOME'), (33, 'TEMPORAL_HOME'), (34, 'TEMPORAL_HOME'),
  (35, 'TEMPORAL_HOME'), (36, 'TEMPORAL_HOME'), (37, 'TEMPORAL_HOME'),
  (38, 'TEMPORAL_HOME'), (39, 'TEMPORAL_HOME'),
  -- Shelters (40-44)
  (40, 'SHELTER'), (41, 'SHELTER'), (42, 'SHELTER'), (43, 'SHELTER'), (44, 'SHELTER'),
  -- Sterilization (45-47)
  (45, 'STERILIZATION_SERVICE'), (46, 'STERILIZATION_SERVICE'), (47, 'STERILIZATION_SERVICE'),
  -- Mixed: RESCUER + PHOTOGRAPHER (48), RESCUER + TEMPORAL_HOME (49), RESCUER + ADOPTER (50)
  (48, 'RESCUER'), (48, 'PHOTOGRAPHER'),
  (49, 'RESCUER'), (49, 'TEMPORAL_HOME'),
  (50, 'RESCUER'), (50, 'ADOPTER')
ON CONFLICT (user_id, role) DO NOTHING;

-- -------------------------
-- PHOTOGRAPHERS
-- -------------------------
INSERT INTO photographers (user_id, photographer_fee, photographer_currency, country, state) VALUES
  (27, 250.00, 'MXN', 'MEXICO',    'CDMX'),
  (28, 300.00, 'MXN', 'MEXICO',    'Jalisco'),
  (29, 180.00, 'ARS', 'ARGENTINA', 'Buenos Aires'),
  (30, 200.00, 'CLP', 'CHILE',     'Región Metropolitana'),
  (31, 150.00, 'COP', 'COLOMBIA',  'Antioquia'),
  (48, 220.00, 'MXN', 'MEXICO',    'Nuevo León')
ON CONFLICT (user_id) DO NOTHING;

-- -------------------------
-- TEMPORAL HOMES
-- -------------------------
INSERT INTO temporal_homes (user_id, alias, country, state, city, zip, neighborhood, created_at) VALUES
  (32, 'Casa Elena',     'MEXICO',    'CDMX',              'Ciudad de México', '06600', 'Roma Norte',      1704412800000),
  (33, 'Hogar Tomás',    'MEXICO',    'Jalisco',            'Guadalajara',      '44100', 'Centro',          1707177600000),
  (34, 'Refugio Lucía',  'ARGENTINA', 'Buenos Aires',       'Buenos Aires',     '1000',  'Palermo',         1709856000000),
  (35, 'Patio Martín',   'ARGENTINA', 'Córdoba',            'Córdoba',          '5000',  'General Paz',     1712534400000),
  (36, 'Casa Rosa',      'CHILE',     'Región Metropolitana','Santiago',         '7500000','Providencia',   1715126400000),
  (37, 'Hogar Félix',    'COLOMBIA',  'Antioquia',          'Medellín',         '050001','El Poblado',      1717804800000),
  (38, 'Casa Catalina',  'MEXICO',    'Nuevo León',         'Monterrey',        '64000', 'San Pedro',       1720396800000),
  (39, 'Hogar Hugo',     'MEXICO',    'Puebla',             'Puebla',           '72000', 'Centro Histórico',1723075200000),
  (49, 'Casa Ernesto',   'MEXICO',    'CDMX',              'Ciudad de México', '11800', 'Polanco',          1715299200000)
ON CONFLICT (user_id) DO NOTHING;

-- -------------------------
-- USER SHELTERS
-- -------------------------
INSERT INTO user_shelters (user_id, name, country, state, city, neighborhood, address, zip, phone, email, website, description, currency, created_at, updated_at) VALUES
  (40, 'Amigos Peludos',  'MEXICO',    'CDMX',              'Ciudad de México', 'Coyoacán',    'Calle Miguel Ángel de Quevedo 123', '04000', '+52 55 1234 5678', 'amigos@amigospeludos.mx',    'https://amigospeludos.mx',    'Refugio dedicado al rescate y adopción de perros y gatos en CDMX.',   'MXN', 1704499200000, 1704499200000),
  (41, 'Refugio Esperanza','MEXICO',   'Jalisco',            'Guadalajara',      'Zapopan',     'Av. Vallarta 567',                  '45010', '+52 33 9876 5432', 'info@esperanzarefugio.mx',    'https://esperanzarefugio.mx', 'Refugio con más de 10 años de experiencia en Jalisco.',                'MXN', 1707264000000, 1707264000000),
  (42, 'Patitas Felices',  'ARGENTINA','Buenos Aires',       'Buenos Aires',     'Villa Urquiza','Av. Triunvirato 1890',             '1431',  '+54 11 4444 3333', 'patitas@patitasfelices.ar',   NULL,                          'Centro de adopción responsable en Buenos Aires.',                      'ARS', 1709942400000, 1709942400000),
  (43, 'Huellitas del Sur','CHILE',    'Región Metropolitana','Santiago',         'La Florida',  'Calle La Serena 234',              '8240000','+56 2 2345 6789', 'huellas@huellitasdelsur.cl',  NULL,                          'Refugio en el sur de Santiago con 200+ animales rescatados.',          'CLP', 1712620800000, 1712620800000),
  (44, 'Nueva Vida Animal','COLOMBIA', 'Antioquia',          'Medellín',         'Laureles',    'Cra. 70 # 44-50',                  '050001','+57 4 567 8901',   'info@nuevavida.co',           'https://nuevavida.co',        'Fundación comprometida con el bienestar animal en Colombia.',          'COP', 1715212800000, 1715212800000)
ON CONFLICT (user_id) DO NOTHING;

-- -------------------------
-- USER STERILIZATION LOCATIONS
-- -------------------------
INSERT INTO user_sterilization_locations (user_id, name, country, state, city, neighborhood, address, zip, phone, email, website, description, created_at, updated_at) VALUES
  (45, 'Clínica Esteriliza Ya', 'MEXICO',    'CDMX',              'Ciudad de México', 'Tlalpan',    'Insurgentes Sur 4512',    '14000', '+52 55 5555 1111', 'esteriliza@ya.mx',         NULL,                        'Esterilizaciones a bajo costo todos los sábados.',           1704585600000, 1704585600000),
  (46, 'Clínica Bienestar',     'MEXICO',    'Jalisco',            'Guadalajara',      'Tetlán',     'Av. 8 de Julio 1500',     '44820', '+52 33 3333 2222', 'clinica@bienestarjal.mx',  'https://bienestarjal.mx',   'Servicio veterinario con programa de esterilización masiva.', 1707350400000, 1707350400000),
  (47, 'Clínica Sana Mascota',  'ARGENTINA', 'Buenos Aires',       'Buenos Aires',     'Belgrano',   'Av. Cabildo 980',         '1426',  '+54 11 5555 6666', 'sanamascota@gmail.com',    NULL,                        'Clínica veterinaria con esterilizaciones gratuitas un día al mes.', 1710028800000, 1710028800000)
ON CONFLICT (user_id) DO NOTHING;

-- -------------------------
-- PETS (200 rows)
-- Rescuers: users 2-16, 48, 49, 50
-- Types: ~140 DOG, ~35 CAT, ~15 BIRD, ~10 FISH
-- -------------------------
INSERT INTO pets (rescuer_id, name, type, breed, description, weight, age_years, age_months, sex, status, color, size, temperament, is_sterilized, is_microchipped, vaccinations, is_good_with_kids, is_good_with_dogs, is_good_with_cats, is_house_trained, energy_level, rescue_date, rescue_location, adoption_fee, currency, is_urgent, is_promoted, created_at) VALUES

-- María García (user 2) - 13 pets: 10 dogs, 2 cats, 1 bird
(2,'Max',      'DOG','Labrador Retriever',    'Perro amigable y juguetón, ideal para familias.', 28.5,3,0,'MALE',  'AVAILABLE','Amarillo', 'LARGE', 'Amigable',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1717372800000,'Parque Centenario, CDMX',  800.00,'MXN',false,true, 1717372800000),
(2,'Luna',     'DOG','Golden Retriever',      'Luna es una perra muy cariñosa y leal.',          27.0,2,0,'FEMALE','AVAILABLE','Dorado',   'LARGE', 'Cariñosa',  true, true,  'Antirrábica, Moquillo, Leptospirosis',true,  true,  true,  true,  'MEDIUM', 1719964800000,'Colonia Roma, CDMX',       850.00,'MXN',false,true, 1719964800000),
(2,'Rocky',    'DOG','German Shepherd',       'Perro inteligente, leal y protector.',            35.0,4,6,'MALE',  'AVAILABLE','Negro y café','LARGE','Leal',     true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'HIGH',   1722643200000,'Tlalpan, CDMX',            900.00,'MXN',false,false,1722643200000),
(2,'Bella',    'DOG','Poodle',                'Bella es muy inteligente y no suelta pelo.',      6.5, 1,3,'FEMALE','AVAILABLE','Blanco',   'SMALL', 'Inteligente',true,true,  'Antirrábica, Moquillo',               true,  true,  true,  true,  'MEDIUM', 1725321600000,'Coyoacán, CDMX',           700.00,'MXN',false,false,1725321600000),
(2,'Chico',    'DOG','Chihuahua',             'Pequeño pero lleno de personalidad.',             2.3, 5,0,'MALE',  'AVAILABLE','Café',     'SMALL', 'Valiente',  false,false, 'Antirrábica',                         false, false, false, true,  'HIGH',   1727913600000,'Iztapalapa, CDMX',         500.00,'MXN',false,false,1727913600000),
(2,'Mia',      'DOG','Beagle',                'Mia es curiosa, alegre y le encanta jugar.',      10.2,2,8,'FEMALE','PENDING',  'Tricolor', 'SMALL', 'Alegre',    true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1730592000000,'Benito Juárez, CDMX',      650.00,'MXN',false,false,1730592000000),
(2,'Thor',     'DOG','Rottweiler',            'Thor es tranquilo y bien entrenado.',             42.0,3,0,'MALE',  'AVAILABLE','Negro y café','LARGE','Tranquilo', true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  false, false, true,  'MEDIUM', 1733184000000,'Gustavo A. Madero, CDMX',  750.00,'MXN',false,false,1733184000000),
(2,'Coco',     'DOG','Cocker Spaniel',        'Coco es dulce y adora los mimos.',                9.8, 1,6,'MALE',  'AVAILABLE','Castaño',  'SMALL', 'Dulce',     true, false, 'Antirrábica, Moquillo',               true,  true,  true,  true,  'MEDIUM', 1735862400000,'Cuauhtémoc, CDMX',         700.00,'MXN',false,false,1735862400000),
(2,'Nala',     'DOG','Border Collie',         'Nala necesita mucho ejercicio y estimulación.',   18.5,2,4,'FEMALE','AVAILABLE','Blanco y negro','MEDIUM','Activa', true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1738540800000,'Azcapotzalco, CDMX',       800.00,'MXN',false,false,1738540800000),
(2,'Rex',      'DOG','Mestizo',               'Rex es un perro callejero rehabilitado.',         15.0,0,10,'MALE', 'AVAILABLE','Café',     'MEDIUM','Tímido',     false,false, 'Antirrábica',                         true,  true,  false, false, 'LOW',    1741132800000,'Mercado Jamaica, CDMX',    300.00,'MXN',true, false,1741132800000),
(2,'Misu',     'CAT','Doméstico Pelo Corto',  'Misu es tranquila y autosuficiente.',             3.5, 2,0,'FEMALE','AVAILABLE','Naranja',  'SMALL', 'Tranquila', true, true,  'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1743811200000,'Colonia Nápoles, CDMX',    400.00,'MXN',false,false,1743811200000),
(2,'Sombra',   'CAT','Siamés',                'Sombra es elegante y muy vocal.',                 4.2, 3,0,'MALE',  'AVAILABLE','Siamés',   'SMALL', 'Vocal',     true, false, 'Triple Felina',                       false, false, true,  true,  'MEDIUM', 1746403200000,'Colonia del Valle, CDMX',  500.00,'MXN',false,false,1746403200000),
(2,'Piolín',   'BIRD','Canario',              'Canta todas las mañanas. Muy alegre.',            0.1, 1,0,'MALE',  'AVAILABLE','Amarillo', 'SMALL', 'Alegre',    false,false, NULL,                                  false, false, false, true,  'LOW',    1748736000000,'Tepito, CDMX',             200.00,'MXN',false,false,1748736000000),

-- Carlos Rodríguez (user 3) - 12 pets: 9 dogs, 2 cats, 1 fish
(3,'Bruno',    'DOG','Bulldog Francés',       'Bruno es calmado, ideal para departamento.',      11.5,4,0,'MALE',  'AVAILABLE','Atigrado',  'SMALL','Calmado',   true, true,  'Antirrábica, Moquillo',               true,  false, true,  true,  'LOW',    1706745600000,'Providencia, Guadalajara',600.00,'MXN',false,false,1706745600000),
(3,'Lola',     'DOG','Dachshund',             'Lola es curiosa y le encanta escarbar.',          5.5, 2,6,'FEMALE','AVAILABLE','Negro y café','SMALL','Curiosa',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'MEDIUM', 1709424000000,'Zapopan, Jalisco',         550.00,'MXN',false,false,1709424000000),
(3,'Duke',     'DOG','Boxer',                 'Duke es enérgico y muy juguetón.',                30.0,1,8,'MALE',  'AVAILABLE','Atigrado',  'LARGE','Enérgico',  false,false, 'Antirrábica',                         true,  true,  false, false, 'HIGH',   1712102400000,'Tlaquepaque, Jalisco',     750.00,'MXN',false,false,1712102400000),
(3,'Kira',     'DOG','Husky Siberiano',       'Kira necesita mucho ejercicio y espacio.',        25.5,3,0,'FEMALE','AVAILABLE','Blanco y gris','LARGE','Independiente',true,false,'Antirrábica, Moquillo, Parvovirus',true,  true,  false, false, 'HIGH',   1714694400000,'Zapopan, Jalisco',         950.00,'MXN',false,true, 1714694400000),
(3,'Pelusa',   'DOG','Pomerania',             'Pelusa es diminuta y muy coqueta.',               2.0, 0,9,'FEMALE','AVAILABLE','Blanco',    'SMALL','Coqueta',   false,false, 'Antirrábica',                         true,  false, true,  false, 'HIGH',   1717372800000,'El Salto, Jalisco',        600.00,'MXN',false,false,1717372800000),
(3,'Titan',    'DOG','Pitbull',               'Titan es muy cariñoso a pesar de su apariencia.', 28.0,5,0,'MALE',  'ADOPTED',  'Café',      'LARGE','Cariñoso',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'MEDIUM', 1719964800000,'Tetlán, Guadalajara',      700.00,'MXN',false,false,1719964800000),
(3,'Mochi',    'DOG','Shih Tzu',              'Mochi es adorable y le encanta que le peinen.',   4.5, 3,0,'FEMALE','AVAILABLE','Blanco y café','SMALL','Adorable',true, false, 'Antirrábica, Moquillo',               true,  true,  true,  true,  'LOW',    1722643200000,'Chapalita, Guadalajara',   650.00,'MXN',false,false,1722643200000),
(3,'Athos',    'DOG','Dálmata',               'Athos es elegante y lleno de energía.',           25.0,2,0,'MALE',  'AVAILABLE','Blanco con puntos','LARGE','Activo',true,true,'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'HIGH',   1725321600000,'Oblatos, Guadalajara',     850.00,'MXN',false,true, 1725321600000),
(3,'Fideo',    'DOG','Mestizo',               'Fideo es flaco pero lleno de amor.',              8.0, 1,4,'MALE',  'AVAILABLE','Amarillo',  'SMALL','Tímido',    false,false, 'Antirrábica',                         true,  true,  true,  false, 'LOW',    1727913600000,'Periférico Norte, GDL',   250.00,'MXN',true, false,1727913600000),
(3,'Gato',     'CAT','Persa',                 'Gato es esponjoso y muy tranquilo.',              4.8, 6,0,'MALE',  'AVAILABLE','Blanco',    'SMALL','Tranquilo', true, false, 'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1730592000000,'Zapopan, Jalisco',         600.00,'MXN',false,false,1730592000000),
(3,'Mia',      'CAT','Ragdoll',               'Mia es enorme y absolutamente adorable.',         6.0, 4,0,'FEMALE','AVAILABLE','Gris',      'SMALL','Adorable',  true, true,  'Triple Felina',                       true,  false, true,  true,  'LOW',    1733184000000,'Chapalita, Guadalajara',   700.00,'MXN',false,false,1733184000000),
(3,'Nemo',     'FISH','Pez Payaso',           'Nemo vive en pecera de agua salada.',             0.05,0,6,'MALE',  'AVAILABLE','Naranja y blanco','SMALL','Activo',false,false,NULL,                                 false, false, false, true,  'MEDIUM', 1735862400000,'Zapopan, Jalisco',         150.00,'MXN',false,false,1735862400000),

-- Ana Martínez (user 4) - 12 pets: 9 dogs, 2 cats, 1 bird
(4,'Zeus',     'DOG','German Shepherd',       'Zeus es un perro de trabajo bien entrenado.',     36.0,4,0,'MALE',  'AVAILABLE','Negro',    'LARGE', 'Leal',      true, true,  'Antirrábica, Moquillo, Parvovirus',   false, true,  false, true,  'HIGH',   1709424000000,'Villa del Parque, CABA',   900.00,'ARS',false,false,1709424000000),
(4,'Azul',     'DOG','Australian Shepherd',   'Azul es brillante y necesita estímulo constante.',22.0,2,0,'FEMALE','AVAILABLE','Merle azul','LARGE','Inteligente',true,true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1712102400000,'Palermo, CABA',            1000.00,'ARS',false,true,1712102400000),
(4,'Copito',   'DOG','Maltés',                'Copito es blanco como la nieve y muy afectuoso.', 2.5, 3,0,'MALE',  'AVAILABLE','Blanco',   'SMALL', 'Afectuoso', true, true,  'Antirrábica, Moquillo',               true,  true,  true,  true,  'LOW',    1714694400000,'Belgrano, CABA',           800.00,'ARS',false,false,1714694400000),
(4,'Tormenta', 'DOG','Husky Siberiano',       'Tormenta tiene ojos azules impresionantes.',      26.0,1,0,'FEMALE','AVAILABLE','Gris y blanco','LARGE','Independiente',true,false,'Antirrábica, Moquillo',            true,  true,  false, false, 'HIGH',   1717372800000,'Caballito, CABA',          950.00,'ARS',false,true, 1717372800000),
(4,'Pumba',    'DOG','Pitbull',               'Pumba ama a los niños y es super paciente.',      29.0,5,0,'MALE',  'ADOPTED',  'Negro',    'LARGE', 'Paciente',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'MEDIUM', 1719964800000,'Flores, CABA',             700.00,'ARS',false,false,1719964800000),
(4,'Chispa',   'DOG','Beagle',                'Chispa es olfateadora nata y muy juguetona.',     11.0,1,8,'FEMALE','AVAILABLE','Tricolor', 'SMALL', 'Juguetona', false,false, 'Antirrábica',                         true,  true,  false, false, 'HIGH',   1722643200000,'Barracas, CABA',           600.00,'ARS',false,false,1722643200000),
(4,'Simba',    'DOG','Mestizo',               'Simba fue rescatado de la calle y es muy agradecido.',14.0,2,0,'MALE','AVAILABLE','Dorado','MEDIUM','Agradecido',false,false,'Antirrábica',                           true,  true,  true,  true,  'MEDIUM', 1725321600000,'Villa Lugano, CABA',       400.00,'ARS',true, false,1725321600000),
(4,'Maga',     'DOG','Poodle Toy',            'Maga es pequeña y no suelta pelo.',               3.2, 4,0,'FEMALE','AVAILABLE','Negro',    'SMALL', 'Inteligente',true,false, 'Antirrábica, Moquillo',               true,  false, true,  true,  'MEDIUM', 1727913600000,'Núñez, CABA',             750.00,'ARS',false,false,1727913600000),
(4,'Hulk',     'DOG','Rottweiler',            'Hulk es grande pero un bebé con su familia.',     45.0,3,6,'MALE',  'AVAILABLE','Negro y café','LARGE','Protector', true, true,  'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'MEDIUM', 1730592000000,'Lomas de Zamora, GBA',    800.00,'ARS',false,false,1730592000000),
(4,'Mimi',     'CAT','Maine Coon',            'Mimi es enorme y majestuosa.',                    7.5, 5,0,'FEMALE','AVAILABLE','Atigrado', 'SMALL', 'Majestuosa',true, false, 'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1733184000000,'Palermo, CABA',            900.00,'ARS',false,true, 1733184000000),
(4,'Negro',    'CAT','Doméstico Pelo Corto',  'Negro es un gato misterioso y muy independiente.',4.0, 7,0,'MALE',  'AVAILABLE','Negro',    'SMALL', 'Misterioso',true, false, 'Triple Felina',                       false, false, true,  true,  'LOW',    1735862400000,'Constitución, CABA',      350.00,'ARS',false,false,1735862400000),
(4,'Tweety',   'BIRD','Periquito',            'Tweety aprende palabras rápido.',                 0.05,0,8,'MALE',  'AVAILABLE','Verde',    'SMALL', 'Parlanchín',false,false, NULL,                                  false, false, false, true,  'MEDIUM', 1738540800000,'Villa Urquiza, CABA',     150.00,'ARS',false,false,1738540800000),

-- Luis Hernández (user 5) - 10 pets: 8 dogs, 1 cat, 1 fish
(5,'Lobo',     'DOG','Mestizo',               'Lobo es grande, protector y muy inteligente.',    22.0,4,0,'MALE',  'AVAILABLE','Gris',     'LARGE', 'Protector', false,false, 'Antirrábica, Moquillo',               false, true,  false, true,  'MEDIUM', 1712102400000,'Monterrey, Nuevo León',   350.00,'MXN',false,false,1712102400000),
(5,'Candy',    'DOG','Golden Retriever',      'Candy es la mejor amiga que puedas tener.',       29.0,2,0,'FEMALE','AVAILABLE','Dorado',  'LARGE',  'Cariñosa',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1714694400000,'San Pedro, Nuevo León',   900.00,'MXN',false,true, 1714694400000),
(5,'Rex',      'DOG','Doberman',              'Rex es veloz, leal y muy elegante.',              35.0,3,0,'MALE',  'AVAILABLE','Negro y café','LARGE','Elegante',  true, true,  'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'HIGH',   1717372800000,'Apodaca, Nuevo León',     850.00,'MXN',false,false,1717372800000),
(5,'Mona',     'DOG','Labrador Retriever',    'Mona es gentil con todos los animales.',          27.0,1,6,'FEMALE','PENDING',  'Negro',   'LARGE',  'Gentil',    true, false, 'Antirrábica, Moquillo',               true,  true,  true,  true,  'MEDIUM', 1719964800000,'Escobedo, Nuevo León',    800.00,'MXN',false,false,1719964800000),
(5,'Flash',    'DOG','Border Collie',         'Flash es el perro más rápido del parque.',        16.0,2,0,'MALE',  'AVAILABLE','Negro y blanco','MEDIUM','Activo', true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1722643200000,'Guadalupe, Nuevo León',   800.00,'MXN',false,false,1722643200000),
(5,'Yuki',     'DOG','Samoyedo',              'Yuki parece un oso de peluche viviente.',         28.0,4,0,'FEMALE','AVAILABLE','Blanco',  'LARGE',  'Dulce',     true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1725321600000,'San Nicolás, Nuevo León', 950.00,'MXN',false,true, 1725321600000),
(5,'Paco',     'DOG','Chihuahua',             'Paco es chico pero muy valiente.',                1.8, 6,0,'MALE',  'AVAILABLE','Café',    'SMALL',  'Valiente',  true, false, 'Antirrábica',                         false, false, false, true,  'HIGH',   1727913600000,'Monterrey Centro',        400.00,'MXN',false,false,1727913600000),
(5,'Nube',     'DOG','Cocker Spaniel',        'Nube tiene las orejas más largas del barrio.',    10.0,2,4,'FEMALE','AVAILABLE','Blanco',  'SMALL',  'Tierna',    true, true,  'Antirrábica, Moquillo',               true,  true,  true,  true,  'LOW',    1730592000000,'Cumbres, Monterrey',      700.00,'MXN',false,false,1730592000000),
(5,'Gris',     'CAT','Russian Blue',          'Gris es elegante y muy leal a su dueño.',         4.5, 3,0,'MALE',  'AVAILABLE','Gris azulado','SMALL','Leal',     true, false, 'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1733184000000,'Monterrey, Nuevo León',   550.00,'MXN',false,false,1733184000000),
(5,'Burbuja',  'FISH','Betta',                'Burbuja tiene colores increíbles.',               0.02,0,4,'MALE',  'AVAILABLE','Azul y rojo','SMALL','Activo',   false,false, NULL,                                  false, false, false, true,  'LOW',    1735862400000,'Monterrey, Nuevo León',   100.00,'MXN',false,false,1735862400000),

-- Sofía López (user 6) - 10 pets: 7 dogs, 2 cats, 1 bird
(6,'Amor',     'DOG','Labrador Retriever',    'Amor rescatada de maltrato, muy dulce ahora.',    25.0,3,0,'FEMALE','AVAILABLE','Chocolate','LARGE', 'Dulce',     true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1714694400000,'Santiago, Chile',         0.00,'CLP', true, true, 1714694400000),
(6,'Perla',    'DOG','Poodle',                'Perla es blanca y muy elegante.',                 7.0, 2,6,'FEMALE','AVAILABLE','Blanco',   'SMALL', 'Elegante',  true, true,  'Antirrábica, Moquillo',               true,  false, true,  true,  'MEDIUM', 1717372800000,'Providencia, Santiago',   0.00,'CLP', false,false,1717372800000),
(6,'León',     'DOG','Mestizo',               'León tiene carácter pero mucho amor que dar.',    18.0,4,0,'MALE',  'AVAILABLE','Café',     'MEDIUM','Valiente',  false,false, 'Antirrábica',                         false, true,  false, false, 'HIGH',   1719964800000,'La Florida, Santiago',    0.00,'CLP', false,false,1719964800000),
(6,'Tina',     'DOG','Beagle',                'Tina adora los olores y las aventuras.',          10.5,1,0,'FEMALE','AVAILABLE','Tricolor', 'SMALL', 'Aventurera',true, false, 'Antirrábica, Moquillo',               true,  true,  true,  true,  'HIGH',   1722643200000,'Ñuñoa, Santiago',         0.00,'CLP', false,false,1722643200000),
(6,'Principe', 'DOG','Shih Tzu',              'Príncipe vive a la altura de su nombre.',         4.8, 5,0,'MALE',  'AVAILABLE','Blanco y café','SMALL','Elegante',true, false, 'Antirrábica, Moquillo',               true,  false, true,  true,  'LOW',    1725321600000,'Las Condes, Santiago',    0.00,'CLP', false,true, 1725321600000),
(6,'Spot',     'DOG','Dálmata',               'Spot tiene 87 manchas y se las sabe todas.',      24.0,2,0,'MALE',  'AVAILABLE','Blanco con puntos','LARGE','Activo',true,true, 'Antirrábica, Moquillo, Parvovirus',  true,  true,  false, true,  'HIGH',   1727913600000,'Peñalolén, Santiago',     0.00,'CLP', false,false,1727913600000),
(6,'Toto',     'DOG','Terrier',               'Toto es pequeño y lleno de energía.',             4.0, 0,10,'MALE', 'AVAILABLE','Blanco',   'SMALL', 'Enérgico',  false,false, 'Antirrábica',                         true,  true,  false, false, 'HIGH',   1730592000000,'Pudahuel, Santiago',      0.00,'CLP', true, false,1730592000000),
(6,'Canela',   'CAT','Bengala',               'Canela tiene el pelaje más exótico.',             5.0, 2,0,'FEMALE','AVAILABLE','Atigrado marrón','SMALL','Activa',true,false,'Triple Felina',                        false, false, true,  true,  'HIGH',   1733184000000,'Vitacura, Santiago',      0.00,'CLP', false,true, 1733184000000),
(6,'Blanca',   'CAT','Doméstico Pelo Largo',  'Blanca es blanca, suave y muy cariñosa.',         3.8, 4,0,'FEMALE','AVAILABLE','Blanco',   'SMALL', 'Cariñosa',  true, false, 'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1735862400000,'Estación Central, SCL',   0.00,'CLP', false,false,1735862400000),
(6,'Loro',     'BIRD','Loro Cotorro',         'Loro dice "hola" y "qué tal" con claridad.',      0.3, 3,0,'MALE',  'AVAILABLE','Verde y rojo','SMALL','Parlanchín',false,false,NULL,                                 false, false, false, true,  'MEDIUM', 1738540800000,'Recoleta, Santiago',      0.00,'CLP', false,false,1738540800000),

-- Pedro González (user 7) - 10 pets: 7 dogs, 2 cats, 1 fish
(7,'Bravo',    'DOG','German Shepherd',       'Bravo es ex-perro de trabajo y muy obediente.',   38.0,6,0,'MALE',  'AVAILABLE','Negro y café','LARGE','Obediente', true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'MEDIUM', 1717372800000,'Medellín, Colombia',      0.00,'COP', false,false,1717372800000),
(7,'Paloma',   'DOG','Golden Retriever',      'Paloma es gentil con niños y adultos mayores.',   28.0,4,0,'FEMALE','AVAILABLE','Dorado',   'LARGE', 'Gentil',    true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1719964800000,'El Poblado, Medellín',    0.00,'COP', false,true, 1719964800000),
(7,'Oso',      'DOG','Akita',                 'Oso es majestuoso y muy leal a una persona.',     40.0,3,0,'MALE',  'AVAILABLE','Blanco',   'LARGE', 'Leal',      true, true,  'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'LOW',    1722643200000,'Laureles, Medellín',      0.00,'COP', false,false,1722643200000),
(7,'Chiqui',   'DOG','Chihuahua',             'Chiqui es pequeñita y muy miedosa al inicio.',    1.9, 2,0,'FEMALE','AVAILABLE','Café claro','SMALL', 'Tímida',    false,false, 'Antirrábica',                         false, false, false, true,  'LOW',    1725321600000,'Envigado, Antioquia',     0.00,'COP', false,false,1725321600000),
(7,'Bello',    'DOG','Mestizo',               'Bello es un perro de la calle con mucho corazón.',12.0,3,0,'MALE',  'AVAILABLE','Negro',    'MEDIUM','Agradecido', false,false,'Antirrábica',                          true,  true,  false, true,  'MEDIUM', 1727913600000,'Bello, Antioquia',        0.00,'COP', true, false,1727913600000),
(7,'Laika',    'DOG','Mestizo',               'Laika es curiosa como la famosa cosmonauta.',     8.0, 1,0,'FEMALE','PENDING',  'Café',     'SMALL', 'Curiosa',   false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1730592000000,'Itagüí, Antioquia',       0.00,'COP', false,false,1730592000000),
(7,'Conan',    'DOG','Rottweiler',            'Conan ya tiene 7 años pero aún tiene mucha vida.', 40.0,7,0,'MALE', 'AVAILABLE','Negro y café','LARGE','Tranquilo', true, true, 'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'LOW',    1733184000000,'Sabaneta, Antioquia',     0.00,'COP', true, false,1733184000000),
(7,'Garfield', 'CAT','Persa',                 'Garfield es exactamente como el de la caricatura.',8.0,5,0,'MALE',  'AVAILABLE','Naranja',  'SMALL', 'Perezoso',  true, false, 'Triple Felina',                       false, false, true,  true,  'LOW',    1735862400000,'El Centro, Medellín',     0.00,'COP', false,true, 1735862400000),
(7,'Michi',    'CAT','Doméstico Pelo Corto',  'Michi es un cazador nato, ojo con los ratones.',  3.5, 2,0,'MALE',  'AVAILABLE','Atigrado', 'SMALL', 'Cazador',   false,false, 'Triple Felina',                       false, false, true,  false, 'MEDIUM', 1738540800000,'Robledo, Medellín',       0.00,'COP', false,false,1738540800000),
(7,'Dory',     'FISH','Pez Cirujano Azul',    'Dory es azul brillante y muy activa.',            0.08,0,3,'FEMALE','AVAILABLE','Azul',     'SMALL', 'Activa',    false,false, NULL,                                  false, false, false, true,  'HIGH',   1741132800000,'Medellín, Colombia',      0.00,'COP', false,false,1741132800000),

-- Valentina Pérez (user 8) - 9 pets: 7 dogs, 1 cat, 1 bird
(8,'Princesa', 'DOG','Poodle',                'Princesa es refinada y muy limpia.',              5.5, 4,0,'FEMALE','AVAILABLE','Plateado', 'SMALL', 'Refinada',  true, true,  'Antirrábica, Moquillo',               true,  false, true,  true,  'LOW',    1722643200000,'Polanco, CDMX',           800.00,'MXN',false,true, 1722643200000),
(8,'Canelo',   'DOG','Mestizo',               'Canelo fue rescatado de las calles de CDMX.',     16.0,2,0,'MALE',  'AVAILABLE','Café',     'MEDIUM','Agradecido', false,false,'Antirrábica',                          true,  true,  false, true,  'MEDIUM', 1725321600000,'Tepito, CDMX',            300.00,'MXN',true, false,1725321600000),
(8,'Duna',     'DOG','Golden Retriever',      'Duna ama nadar en el lago del parque.',           27.5,3,0,'FEMALE','AVAILABLE','Dorado',   'LARGE', 'Activa',    true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1727913600000,'Santa Fe, CDMX',          900.00,'MXN',false,false,1727913600000),
(8,'Pirata',   'DOG','Mestizo',               'Pirata tiene un ojo de cada color, es único.',    20.0,5,0,'MALE',  'AVAILABLE','Blanco y negro','LARGE','Único',  false,true,  'Antirrábica, Moquillo',               false, true,  false, true,  'MEDIUM', 1730592000000,'Xochimilco, CDMX',        400.00,'MXN',false,true, 1730592000000),
(8,'Cookie',   'DOG','Labrador Retriever',    'Cookie adora las galletas y los abrazos.',        30.0,1,4,'FEMALE','AVAILABLE','Chocolate','LARGE', 'Cariñosa',  false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1733184000000,'Pedregal, CDMX',          800.00,'MXN',false,false,1733184000000),
(8,'Rufus',    'DOG','Dachshund',             'Rufus es alargado y muy gracioso al correr.',     6.0, 3,0,'MALE',  'ADOPTED',  'Rojo',     'SMALL', 'Gracioso',  true, true,  'Antirrábica, Moquillo',               true,  true,  false, true,  'MEDIUM', 1735862400000,'Pedregal, CDMX',          550.00,'MXN',false,false,1735862400000),
(8,'Stitch',   'DOG','Mestizo',               'Stitch es raro y extraño pero adorable.',         14.0,0,11,'MALE', 'AVAILABLE','Azulado',  'MEDIUM','Peculiar',   false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1738540800000,'Tláhuac, CDMX',           300.00,'MXN',true, false,1738540800000),
(8,'Kiki',     'CAT','Scottish Fold',         'Kiki tiene las orejas dobladas y es adorable.',   3.9, 1,0,'FEMALE','AVAILABLE','Gris',     'SMALL', 'Adorable',  false,false, 'Triple Felina',                       false, false, true,  true,  'LOW',    1741132800000,'Nápoles, CDMX',           650.00,'MXN',false,true, 1741132800000),
(8,'Loro Pepito','BIRD','Guacamaya',          'Pepito repite todo lo que escucha.',              0.9, 5,0,'MALE',  'AVAILABLE','Rojo azul verde','SMALL','Parlanchín',false,false,NULL,                              false, false, false, true,  'MEDIUM', 1743811200000,'Tepito, CDMX',            500.00,'MXN',false,false,1743811200000),

-- Diego Sánchez (user 9) - 9 pets: 7 dogs, 1 cat, 1 fish
(9,'Hércules', 'DOG','Rottweiler',            'Hércules es enorme pero un bebé con su familia.', 48.0,4,0,'MALE',  'AVAILABLE','Negro y café','LARGE','Protector', true, true,  'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'MEDIUM', 1722643200000,'Medellín, Colombia',      0.00,'COP', false,false,1722643200000),
(9,'Mora',     'DOG','Border Collie',         'Mora aprende trucos en minutos.',                 17.0,2,4,'FEMALE','AVAILABLE','Negro y blanco','MEDIUM','Inteligente',true,true,'Antirrábica, Moquillo, Parvovirus',   true, true,  true,  true,  'HIGH',   1725321600000,'Manrique, Medellín',      0.00,'COP', false,true, 1725321600000),
(9,'Trueno',   'DOG','Mestizo',               'Trueno llegó durante una tormenta.',              25.0,3,0,'MALE',  'AVAILABLE','Oscuro',   'LARGE', 'Salvaje',   false,false, 'Antirrábica',                         false, true,  false, false, 'HIGH',   1727913600000,'La América, Medellín',    0.00,'COP', false,false,1727913600000),
(9,'Popi',     'DOG','Pomerania',             'Popi cabe en tu bolso pero no quiere entrar.',    2.2, 1,6,'FEMALE','AVAILABLE','Naranja',  'SMALL', 'Diva',      false,false, 'Antirrábica',                         true,  false, false, false, 'HIGH',   1730592000000,'Belén, Medellín',         0.00,'COP', false,false,1730592000000),
(9,'Gandalf',  'DOG','Schnauzer',             'Gandalf tiene barba y mucha sabiduría.',          15.0,7,0,'MALE',  'AVAILABLE','Gris',     'MEDIUM','Sabio',      true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'LOW',    1733184000000,'Estadio, Medellín',       0.00,'COP', true, false,1733184000000),
(9,'Frida',    'DOG','Labrador Retriever',    'Frida es una labrador negra muy elegante.',       28.5,2,0,'FEMALE','PENDING',  'Negro',    'LARGE', 'Elegante',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1735862400000,'Envigado, Antioquia',     0.00,'COP', false,false,1735862400000),
(9,'Tobías',   'DOG','Mestizo',               'Tobías es callado y muy observador.',             10.0,5,0,'MALE',  'AVAILABLE','Café',     'SMALL', 'Observador',false,false, 'Antirrábica',                         true,  false, true,  true,  'LOW',    1738540800000,'Aranjuez, Medellín',      0.00,'COP', false,false,1738540800000),
(9,'Perla',    'CAT','Doméstico Pelo Corto',  'Perla es suave como la seda.',                    3.2, 1,6,'FEMALE','AVAILABLE','Blanco',   'SMALL', 'Suave',     false,false, 'Triple Felina',                       false, false, true,  true,  'LOW',    1741132800000,'Laureles, Medellín',      0.00,'COP', false,false,1741132800000),
(9,'Goldie',   'FISH','Goldfish',             'Goldie trae buena suerte según dicen.',           0.03,0,5,'MALE',  'AVAILABLE','Dorado',   'SMALL', 'Tranquilo', false,false, NULL,                                  false, false, false, true,  'LOW',    1743811200000,'Medellín, Colombia',      0.00,'COP', false,false,1743811200000),

-- Isabela Ramírez (user 10) - 9 pets: 7 dogs, 1 cat, 1 bird
(10,'Cleo',    'DOG','Golden Retriever',      'Cleo es una golden con energía infinita.',        26.0,1,0,'FEMALE','AVAILABLE','Dorado',   'LARGE', 'Enérgica',  false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1725321600000,'Bogotá, Colombia',        0.00,'COP', false,false,1725321600000),
(10,'Paco',    'DOG','Bulldog Inglés',        'Paco es gordo, ronca y es adorable.',             24.0,4,0,'MALE',  'AVAILABLE','Atigrado', 'MEDIUM','Adorable',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'LOW',    1727913600000,'Chapinero, Bogotá',       0.00,'COP', false,true, 1727913600000),
(10,'Selena',  'DOG','Mestizo',               'Selena es sensible y necesita amor y paciencia.', 9.0, 2,0,'FEMALE','AVAILABLE','Café claro','SMALL','Sensible',  false,false, 'Antirrábica',                         false, false, false, false, 'LOW',    1730592000000,'Kennedy, Bogotá',         0.00,'COP', true, false,1730592000000),
(10,'Kobe',    'DOG','Labrador Retriever',    'Kobe siempre trae la pelota de vuelta.',          31.0,3,0,'MALE',  'AVAILABLE','Negro',    'LARGE', 'Juguetón',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1733184000000,'Usaquén, Bogotá',         0.00,'COP', false,false,1733184000000),
(10,'Bella',   'DOG','Cocker Spaniel',        'Bella tiene el pelo más sedoso del vecindario.',  9.5, 2,8,'FEMALE','AVAILABLE','Dorado',   'SMALL', 'Cariñosa',  true, false, 'Antirrábica, Moquillo',               true,  true,  true,  true,  'LOW',    1735862400000,'Suba, Bogotá',            0.00,'COP', false,false,1735862400000),
(10,'Thor',    'DOG','Husky Siberiano',       'Thor tiene los ojos heterocromáticos.',           27.0,2,0,'MALE',  'AVAILABLE','Gris',     'LARGE', 'Independiente',true,false,'Antirrábica, Moquillo, Parvovirus',  false, true,  false, false, 'HIGH',   1738540800000,'Fontibón, Bogotá',        0.00,'COP', false,true, 1738540800000),
(10,'Nena',    'DOG','Mestizo',               'Nena fue abandonada con sus cachorros.',          12.0,3,0,'FEMALE','AVAILABLE','Café',     'MEDIUM','Maternal',   false,false, 'Antirrábica',                         true,  true,  true,  true,  'MEDIUM', 1741132800000,'Bosa, Bogotá',            0.00,'COP', true, false,1741132800000),
(10,'Lince',   'CAT','Abisinio',              'Lince es ágil como un felino salvaje.',           4.2, 3,0,'MALE',  'AVAILABLE','Leonado',  'SMALL', 'Ágil',      true, false, 'Triple Felina',                       false, false, true,  false, 'HIGH',   1743811200000,'Teusaquillo, Bogotá',     0.00,'COP', false,false,1743811200000),
(10,'Pico',    'BIRD','Loro Amazónico',       'Pico canta canciones y cuenta chistes.',          0.4, 7,0,'MALE',  'AVAILABLE','Verde',    'SMALL', 'Chistoso',  false,false, NULL,                                  false, false, false, true,  'LOW',    1746403200000,'Chapinero, Bogotá',       0.00,'COP', false,true, 1746403200000),

-- Mateo Flores (user 11) - 9 pets: 7 dogs, 1 cat, 1 fish
(11,'Bolt',    'DOG','Dálmata',               'Bolt es rápido como su nombre indica.',           23.0,2,0,'MALE',  'AVAILABLE','Blanco con puntos','LARGE','Veloz', true,true, 'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'HIGH',   1727913600000,'Monterrey, NL',           800.00,'MXN',false,false,1727913600000),
(11,'Sasha',   'DOG','German Shepherd',       'Sasha es hembra de trabajo muy equilibrada.',     32.0,3,0,'FEMALE','AVAILABLE','Negro y café','LARGE','Equilibrada',true,true, 'Antirrábica, Moquillo, Parvovirus',  true,  true,  false, true,  'HIGH',   1730592000000,'García, Nuevo León',      900.00,'MXN',false,false,1730592000000),
(11,'Pulga',   'DOG','Chihuahua',             'Pulga es tan chiquita que parece un insecto.',    1.5, 0,8,'FEMALE','AVAILABLE','Café claro','SMALL','Miedosa',    false,false, 'Antirrábica',                         false, false, false, false, 'MEDIUM', 1733184000000,'Apodaca, NL',             350.00,'MXN',false,false,1733184000000),
(11,'Kaiser',  'DOG','Doberman',              'Kaiser es majestuoso, rápido y muy leal.',        37.0,5,0,'MALE',  'AVAILABLE','Negro y café','LARGE','Majestuoso',true,true,  'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'HIGH',   1735862400000,'San Nicolás, NL',         950.00,'MXN',false,true, 1735862400000),
(11,'Nena',    'DOG','Golden Retriever',      'Nena es la primera en recibirte al llegar.',      28.0,4,0,'FEMALE','PENDING',  'Dorado',   'LARGE', 'Amorosa',   true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1738540800000,'Santa Catarina, NL',      900.00,'MXN',false,false,1738540800000),
(11,'Croqueta','DOG','Dachshund',             'Croqueta rueda más de lo que corre.',             7.5, 2,0,'FEMALE','AVAILABLE','Rojo',     'SMALL', 'Graciosa',  true, true,  'Antirrábica, Moquillo',               true,  true,  true,  true,  'LOW',    1741132800000,'Juárez, NL',              550.00,'MXN',false,false,1741132800000),
(11,'Pancho',  'DOG','Mestizo',               'Pancho es tranquilo y perfecto para departamento.',11.0,6,0,'MALE', 'AVAILABLE','Gris',     'MEDIUM','Tranquilo',  false,true,  'Antirrábica, Moquillo',               true,  true,  true,  true,  'LOW',    1743811200000,'Monterrey Centro',        300.00,'MXN',false,false,1743811200000),
(11,'Azul',    'CAT','Azul Ruso',             'Azul es elegante y muy silencioso.',              4.1, 2,0,'MALE',  'AVAILABLE','Gris azulado','SMALL','Silencioso', true,false,'Triple Felina',                       false, false, true,  true,  'LOW',    1746403200000,'Del Valle, Monterrey',    500.00,'MXN',false,false,1746403200000),
(11,'Nemo',    'FISH','Pez Payaso',           'Nemo es muy curioso y sociable.',                 0.04,0,5,'MALE',  'AVAILABLE','Naranja y blanco','SMALL','Curioso',false,false,NULL,                                false, false, false, true,  'MEDIUM', 1748736000000,'Monterrey, NL',           100.00,'MXN',false,false,1748736000000),

-- Camila Torres (user 12) - 9 pets: 7 dogs, 1 cat, 1 bird
(12,'Rayo',    'DOG','Border Collie',         'Rayo aprende trucos a la velocidad del rayo.',    18.0,1,6,'MALE',  'AVAILABLE','Negro y blanco','MEDIUM','Brillante',true,true,'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1730592000000,'Lima, Perú',              0.00,'PEN', false,false,1730592000000),
(12,'Lupe',    'DOG','Labrador Retriever',    'Lupe es gentil con los más pequeños.',            27.5,3,0,'FEMALE','AVAILABLE','Amarillo', 'LARGE', 'Gentil',    true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1733184000000,'Miraflores, Lima',        0.00,'PEN', false,true, 1733184000000),
(12,'Bruno',   'DOG','Boxer',                 'Bruno es músculos y ternura en partes iguales.',  32.0,4,0,'MALE',  'AVAILABLE','Atigrado', 'LARGE', 'Tierno',    true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'HIGH',   1735862400000,'San Isidro, Lima',        0.00,'PEN', false,false,1735862400000),
(12,'Cleo',    'DOG','Basset Hound',          'Cleo tiene las orejas más largas del mundo.',     24.0,5,0,'FEMALE','AVAILABLE','Tricolor', 'MEDIUM','Perezosa',   true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'LOW',    1738540800000,'Barranco, Lima',          0.00,'PEN', false,true, 1738540800000),
(12,'Max',     'DOG','Poodle',                'Max es negro, rizado y muy inteligente.',         6.8, 2,0,'MALE',  'AVAILABLE','Negro',    'SMALL', 'Inteligente',true,false, 'Antirrábica, Moquillo',               true,  false, true,  true,  'MEDIUM', 1741132800000,'Surco, Lima',             0.00,'PEN', false,false,1741132800000),
(12,'Dulce',   'DOG','Maltés',                'Dulce es suave como el azúcar.',                  2.8, 3,0,'FEMALE','AVAILABLE','Blanco',   'SMALL', 'Suave',     true, false, 'Antirrábica, Moquillo',               true,  false, true,  true,  'LOW',    1743811200000,'La Molina, Lima',         0.00,'PEN', false,false,1743811200000),
(12,'Capitan', 'DOG','Mestizo',               'Capitán cuida a todos los demás perros del refugio.',18.0,4,0,'MALE','AVAILABLE','Negro',   'MEDIUM','Líder',      false,false, 'Antirrábica',                         true,  true,  false, true,  'MEDIUM', 1746403200000,'Villa El Salvador, Lima', 0.00,'PEN', false,false,1746403200000),
(12,'Tigre',   'CAT','Atigrado',              'Tigre tiene el pelaje más hermoso del refugio.',  4.8, 4,0,'MALE',  'AVAILABLE','Atigrado', 'SMALL', 'Independiente',true,false,'Triple Felina',                       false, false, true,  false, 'MEDIUM', 1748736000000,'Chorrillos, Lima',        0.00,'PEN', false,false,1748736000000),
(12,'Pepito',  'BIRD','Cotorra',              'Pepito dice "Buenos días" todas las mañanas.',    0.12,2,0,'MALE',  'AVAILABLE','Verde',    'SMALL', 'Educado',   false,false, NULL,                                  false, false, false, true,  'LOW',     1750550400000,'Lima, Perú',              0.00,'PEN', false,false,1750550400000),

-- Alejandro Jiménez (user 13) - 8 pets: 6 dogs, 1 cat, 1 fish
(13,'Apolo',   'DOG','Saluki',                'Apolo es un galgo árabe muy elegante.',           20.0,3,0,'MALE',  'AVAILABLE','Crema',    'LARGE', 'Elegante',  true, false, 'Antirrábica, Moquillo',               true,  true,  false, true,  'HIGH',   1733184000000,'Buenos Aires, Argentina', 0.00,'ARS', false,true, 1733184000000),
(13,'Gala',    'DOG','Setter Irlandés',       'Gala tiene el pelaje rojo más hermoso.',          28.0,2,0,'FEMALE','AVAILABLE','Rojo',     'LARGE', 'Elegante',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1735862400000,'Palermo, CABA',           0.00,'ARS', false,false,1735862400000),
(13,'Yoda',    'DOG','Chihuahua',             'Yoda tiene las orejas de Yoda y la sabiduría también.',1.7,8,0,'MALE','AVAILABLE','Café claro','SMALL','Sabio',  true, false, 'Antirrábica',                         false, false, true,  true,  'LOW',    1738540800000,'Belgrano, CABA',          0.00,'ARS', false,false,1738540800000),
(13,'Campeón', 'DOG','Labrador Retriever',    'Campeón gana concursos de ternura.',              29.0,1,6,'MALE',  'AVAILABLE','Amarillo', 'LARGE', 'Tierno',    false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1741132800000,'Villa del Parque, CABA',  0.00,'ARS', false,false,1741132800000),
(13,'Lila',    'DOG','Weimaraner',            'Lila tiene ojos plateados hipnóticos.',           32.0,4,0,'FEMALE','AVAILABLE','Gris',     'LARGE', 'Hipnótica', true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1743811200000,'Núñez, CABA',             0.00,'ARS', false,true, 1743811200000),
(13,'Cachi',   'DOG','Mestizo',               'Cachi es porteño puro, callejero rehabilitado.',  14.0,3,0,'MALE',  'AVAILABLE','Café',     'MEDIUM','Porteño',   false,false, 'Antirrábica',                         true,  true,  false, true,  'MEDIUM', 1746403200000,'La Boca, CABA',           0.00,'ARS', true, false,1746403200000),
(13,'Nina',    'CAT','Europea Común',         'Nina es una gata callejera domesticada.',         3.8, 5,0,'FEMALE','AVAILABLE','Tricolor', 'SMALL', 'Independiente',true,false,'Triple Felina',                       false, false, true,  false, 'LOW',    1748736000000,'Mataderos, CABA',         0.00,'ARS', false,false,1748736000000),
(13,'Pez Espada','FISH','Pez Espada',         'Pez Espada es rápido y colorido.',                0.05,0,4,'MALE',  'AVAILABLE','Naranja',  'SMALL', 'Activo',    false,false, NULL,                                  false, false, false, true,  'HIGH',   1750550400000,'Buenos Aires, Argentina', 0.00,'ARS', false,false,1750550400000),

-- Luciana Morales (user 14) - 8 pets: 6 dogs, 1 cat, 1 bird
(14,'Estrella','DOG','Australian Shepherd',   'Estrella tiene ojos de distintos colores.',       21.0,2,0,'FEMALE','AVAILABLE','Merle rojo','LARGE','Mágica',    true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1735862400000,'Guadalajara, Jalisco',    800.00,'MXN',false,true, 1735862400000),
(14,'Magno',   'DOG','Great Dane',            'Magno es enorme pero cree que es un perrito.',    65.0,3,0,'MALE',  'AVAILABLE','Arlequín', 'LARGE', 'Gentil',    true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'LOW',    1738540800000,'Zapopan, Jalisco',        1200.00,'MXN',false,true,1738540800000),
(14,'Pinta',   'DOG','Mestizo',               'Pinta tiene manchas de todos los colores.',       13.0,4,0,'FEMALE','AVAILABLE','Manchada', 'MEDIUM','Peculiar',   false,false, 'Antirrábica',                         true,  true,  true,  true,  'MEDIUM', 1741132800000,'Tonalá, Jalisco',         300.00,'MXN',false,false,1741132800000),
(14,'Ajax',    'DOG','German Shepherd',       'Ajax es un pastor ex-policía rehabilitado.',      40.0,7,0,'MALE',  'AVAILABLE','Negro',    'LARGE', 'Obediente', true, true,  'Antirrábica, Moquillo, Parvovirus',   false, true,  false, true,  'MEDIUM', 1743811200000,'Tlaquepaque, Jalisco',    600.00,'MXN',true, false,1743811200000),
(14,'Cinna',   'DOG','Poodle',                'Cinna es albaricoque y muy cariñosa.',            5.8, 1,8,'FEMALE','AVAILABLE','Albaricoque','SMALL','Cariñosa',  true,false, 'Antirrábica, Moquillo',               true,  false, true,  true,  'MEDIUM', 1746403200000,'Guadalajara Centro',      700.00,'MXN',false,false,1746403200000),
(14,'Rocco',   'DOG','Bulldog Inglés',        'Rocco es sólido como una roca y igual de tranquilo.',25.0,5,0,'MALE','AVAILABLE','Rojo',    'MEDIUM','Tranquilo',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'LOW',    1748736000000,'Colonia Americana, GDL',  800.00,'MXN',false,false,1748736000000),
(14,'Violeta', 'CAT','Persa',                 'Violeta es rosada casi y pura elegancia.',         4.5, 3,0,'FEMALE','AVAILABLE','Crema',   'SMALL', 'Elegante',  true, false, 'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1750550400000,'Providencia, GDL',        600.00,'MXN',false,true, 1750550400000),
(14,'Kirin',   'BIRD','Agaporni',             'Kirin es un inseparable que vive con su pareja.', 0.06,1,0,'FEMALE','AVAILABLE','Verde y naranja','SMALL','Social',false,false,NULL,                                  false, false, false, true,  'MEDIUM', 1750636800000,'Zapopan, Jalisco',        250.00,'MXN',false,false,1750636800000),

-- Sebastián Vargas (user 15) - 8 pets: 6 dogs, 1 cat, 1 fish
(15,'Neo',     'DOG','Mestizo',               'Neo es el elegido del refugio, muy especial.',    17.0,2,0,'MALE',  'AVAILABLE','Negro',    'MEDIUM','Especial',   false,false, 'Antirrábica',                         true,  true,  true,  true,  'MEDIUM', 1738540800000,'Santiago, Chile',         0.00,'CLP', false,true, 1738540800000),
(15,'Aurora',  'DOG','Samoyedo',              'Aurora brilla como la aurora boreal.',             26.0,3,0,'FEMALE','AVAILABLE','Blanco',  'LARGE', 'Luminosa',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1741132800000,'Las Condes, Santiago',    0.00,'CLP', false,true, 1741132800000),
(15,'Dante',   'DOG','Xoloitzcuintle',       'Dante es un perro sin pelo, único en su estilo.', 14.0,4,0,'MALE',  'AVAILABLE','Gris oscuro','MEDIUM','Único',    true, true,  'Antirrábica',                         true,  true,  true,  true,  'LOW',    1743811200000,'Ñuñoa, Santiago',         0.00,'CLP', false,false,1743811200000),
(15,'Bella',   'DOG','Golden Retriever',      'Bella es una golden clásica, perfecta.',          28.0,2,6,'FEMALE','AVAILABLE','Dorado',  'LARGE', 'Perfecta',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1746403200000,'Vitacura, Santiago',      0.00,'CLP', false,false,1746403200000),
(15,'Inca',    'DOG','Mestizo',               'Inca tiene sangre de perro andino, muy resistente.',20.0,5,0,'MALE','AVAILABLE','Café',    'MEDIUM','Resistente', false,false, 'Antirrábica',                         true,  true,  false, true,  'LOW',    1748736000000,'Pudahuel, Santiago',      0.00,'CLP', false,false,1748736000000),
(15,'Penny',   'DOG','Labrador Retriever',    'Penny vale su peso en oro y en amor.',            29.0,1,0,'FEMALE','PENDING',  'Chocolate','LARGE','Valiosa',    false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1750550400000,'La Florida, Santiago',    0.00,'CLP', false,false,1750550400000),
(15,'Jade',    'CAT','Birmano',               'Jade tiene los ojos más verdes que hayas visto.', 4.3, 3,0,'FEMALE','AVAILABLE','Seal point','SMALL','Misteriosa',true,false, 'Triple Felina',                       false, false, true,  true,  'LOW',    1750636800000,'Providencia, Santiago',   0.00,'CLP', false,false,1750636800000),
(15,'Guppy',   'FISH','Guppy',                'Guppy tiene la cola más larga de la pecera.',     0.01,0,3,'MALE',  'AVAILABLE','Azul y rojo','SMALL','Activo',   false,false, NULL,                                  false, false, false, true,  'LOW',    1750723200000,'Santiago, Chile',         0.00,'CLP', false,false,1750723200000),

-- Valeria Castillo (user 16) - 7 pets: 5 dogs, 1 cat, 1 bird
(16,'Oreo',    'DOG','Border Collie',         'Oreo es negro y blanco como la galleta.',         17.5,2,0,'MALE',  'AVAILABLE','Negro y blanco','MEDIUM','Activo', true,true, 'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1741132800000,'Monterrey, NL',           800.00,'MXN',false,false,1741132800000),
(16,'Suri',    'DOG','Shih Tzu',              'Suri es como una muñeca de porcelana viva.',      4.2, 4,0,'FEMALE','AVAILABLE','Blanco y gris','SMALL','Delicada',true,false, 'Antirrábica, Moquillo',               true,  false, true,  true,  'LOW',    1743811200000,'San Pedro, NL',           700.00,'MXN',false,false,1743811200000),
(16,'Konan',   'DOG','Mastín Napolitano',     'Konan es gigante con mucha baba y mucho amor.',   65.0,5,0,'MALE',  'AVAILABLE','Gris',     'LARGE', 'Enorme',    true, true,  'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'LOW',    1746403200000,'Apodaca, NL',             900.00,'MXN',false,false,1746403200000),
(16,'Nube',    'DOG','Mestizo',               'Nube es suave y ligera como las nubes.',          8.5, 1,2,'FEMALE','AVAILABLE','Blanco',   'SMALL', 'Suave',     false,false, 'Antirrábica',                         true,  true,  true,  false, 'MEDIUM', 1748736000000,'García, NL',              300.00,'MXN',true, false,1748736000000),
(16,'Boby',    'DOG','Labrador Retriever',    'Boby es el clásico labrador familiar.',           30.0,3,0,'MALE',  'AVAILABLE','Negro',    'LARGE', 'Clásico',   true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1750550400000,'Juárez, NL',             800.00,'MXN',false,false,1750550400000),
(16,'Marte',   'CAT','Doméstico Pelo Corto',  'Marte es naranja y siempre pide comida.',         3.8, 2,0,'MALE',  'AVAILABLE','Naranja',  'SMALL', 'Glotón',    false,false, 'Triple Felina',                       false, false, true,  true,  'LOW',    1750636800000,'Monterrey Centro',        350.00,'MXN',false,false,1750636800000),
(16,'Plumas',  'BIRD','Cacatúa',              'Plumas tiene el copete más espectacular.',        0.35,8,0,'FEMALE','AVAILABLE','Blanco',   'SMALL', 'Diva',      false,false, NULL,                                  false, false, false, true,  'LOW',    1750723200000,'Monterrey, NL',           600.00,'MXN',false,true, 1750723200000),

-- Carmen Rescatadora (user 48, RESCUER+PHOTOGRAPHER) - 5 pets
(48,'Pincel',  'DOG','Mestizo',               'Pincel tiene manchas de pintura en el pelaje.',   9.0, 1,6,'MALE',  'AVAILABLE','Manchado', 'SMALL', 'Artístico', false,false, 'Antirrábica',                         true,  true,  true,  false, 'MEDIUM', 1743811200000,'Roma Norte, CDMX',        350.00,'MXN',false,true, 1743811200000),
(48,'Foto',    'DOG','Dálmata',               'Foto siempre sale perfecto en las fotos.',        22.0,3,0,'MALE',  'AVAILABLE','Blanco con puntos','LARGE','Fotogénico',true,true,'Antirrábica, Moquillo, Parvovirus', true,  true,  false, true,  'MEDIUM', 1746403200000,'Condesa, CDMX',           850.00,'MXN',false,true, 1746403200000),
(48,'Lente',   'DOG','Golden Retriever',      'Lente tiene la mirada más expresiva del mundo.',  26.5,2,0,'FEMALE','AVAILABLE','Dorado',  'LARGE', 'Expresiva', true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1748736000000,'Polanco, CDMX',           900.00,'MXN',false,true, 1748736000000),
(48,'Flash',   'CAT','Bengala',               'Flash es rápido como un relámpago.',              4.7, 2,0,'MALE',  'AVAILABLE','Atigrado leopardo','SMALL','Veloz',true,false,'Triple Felina',                       false, false, true,  false, 'HIGH',   1750550400000,'Juárez, CDMX',            600.00,'MXN',false,false,1750550400000),
(48,'Zoom',    'BIRD','Periquito Australiano','Zoom vuela a toda velocidad por la habitación.',  0.04,0,7,'MALE',  'AVAILABLE','Azul',    'SMALL', 'Veloz',     false,false, NULL,                                  false, false, false, true,  'HIGH',   1750636800000,'Centro, CDMX',            180.00,'MXN',false,false,1750636800000),

-- Ernesto Multirol (user 49, RESCUER+TEMPORAL_HOME) - 5 pets
(49,'Sombra',  'DOG','Mestizo',               'Sombra te sigue a todos lados.',                  12.0,2,0,'MALE',  'AVAILABLE','Negro',    'MEDIUM','Fiel',       false,false, 'Antirrábica',                         true,  true,  false, true,  'MEDIUM', 1745971200000,'Polanco, CDMX',           300.00,'MXN',false,false,1745971200000),
(49,'Dulce',   'DOG','Labrador Retriever',    'Dulce es tan dulce como su nombre.',              26.0,1,8,'FEMALE','AVAILABLE','Chocolate','LARGE', 'Dulce',     false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1747958400000,'Lomas de Chapultepec',    800.00,'MXN',false,false,1747958400000),
(49,'Loki',    'DOG','Husky Siberiano',       'Loki siempre está tramando algo.',                24.0,3,0,'MALE',  'AVAILABLE','Gris y blanco','LARGE','Travieso', true,false,'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, false, 'HIGH',   1749340800000,'Colonia Anzures, CDMX',   950.00,'MXN',false,false,1749340800000),
(49,'Chispa',  'CAT','Doméstico Pelo Corto',  'Chispa es pequeña y llena de energía.',           2.9, 0,8,'FEMALE','AVAILABLE','Naranja atigrado','SMALL','Energética',false,false,'Triple Felina',                 false, false, true,  false, 'HIGH',   1750550400000,'Polanco, CDMX',           300.00,'MXN',false,false,1750550400000),
(49,'Goldie',  'FISH','Goldfish',             'Goldie es el pez más anciano del barrio.',        0.04,3,0,'MALE',  'AVAILABLE','Dorado',  'SMALL', 'Sabio',     false,false, NULL,                                  false, false, false, true,  'LOW',    1750636800000,'Polanco, CDMX',            80.00,'MXN',false,false,1750636800000),

-- Patricia Corazón (user 50, RESCUER+ADOPTER) - 5 pets
(50,'Corazon', 'DOG','Mestizo',               'Corazón fue rescatado el día de San Valentín.',   11.0,1,0,'MALE',  'AVAILABLE','Rojo',     'SMALL', 'Romántico', false,false, 'Antirrábica',                         true,  true,  true,  true,  'MEDIUM', 1739145600000,'Tlalpan, CDMX',           300.00,'MXN',true, false,1739145600000),
(50,'Fiona',   'DOG','Golden Retriever',      'Fiona es princesa en busca de su hogar.',         27.0,4,0,'FEMALE','AVAILABLE','Dorado',  'LARGE', 'Princesa',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'MEDIUM', 1741219200000,'Coyoacán, CDMX',          900.00,'MXN',false,true, 1741219200000),
(50,'Nacho',   'DOG','Chihuahua',             'Nacho es sabroso y viene con mucho carácter.',    2.1, 7,0,'MALE',  'AVAILABLE','Café',     'SMALL', 'Picante',   true, false, 'Antirrábica',                         false, false, false, true,  'HIGH',   1743897600000,'Benito Juárez, CDMX',     450.00,'MXN',false,false,1743897600000),
(50,'Mila',    'CAT','Doméstico Pelo Corto',  'Mila es la más pequeña del refugio.',             2.5, 0,4,'FEMALE','AVAILABLE','Gris',    'SMALL', 'Pequeña',   false,false, 'Triple Felina',                       false, false, true,  false, 'MEDIUM', 1748736000000,'Doctores, CDMX',          250.00,'MXN',false,false,1748736000000),
(50,'Quetzal', 'BIRD','Perico',               'Quetzal tiene los colores del quetzal sagrado.',  0.08,1,0,'MALE',  'AVAILABLE','Verde y rojo','SMALL','Colorido', false,false, NULL,                                  false, false, false, true,  'LOW',    1750550400000,'Xochimilco, CDMX',        200.00,'MXN',false,false,1750550400000);

-- -------------------------
-- SOME ADOPTION REQUESTS
-- -------------------------
INSERT INTO adoption_requests (pet_id, adopter_id, message, status, created_at)
SELECT p.id, 17, 'Me encantaría adoptar a ' || p.name || '. Tenemos espacio y mucho amor.', 'PENDING', 1750550400000
FROM pets p WHERE p.name = 'Max' AND p.rescuer_id = 2;

INSERT INTO adoption_requests (pet_id, adopter_id, message, status, created_at)
SELECT p.id, 18, 'Busco un perro para mi familia con tres hijos. ' || p.name || ' parece perfecto.', 'APPROVED', 1750550400000
FROM pets p WHERE p.name = 'Titan' AND p.rescuer_id = 3;

INSERT INTO adoption_requests (pet_id, adopter_id, message, status, created_at)
SELECT p.id, 19, 'He perdido a mi perra recientemente y ' || p.name || ' me robó el corazón.', 'PENDING', 1750550400000
FROM pets p WHERE p.name = 'Candy' AND p.rescuer_id = 5;

INSERT INTO adoption_requests (pet_id, adopter_id, message, status, created_at)
SELECT p.id, 20, 'Soy adoptante aprobado. Quisiera conocer a ' || p.name || '.', 'PENDING', 1750550400000
FROM pets p WHERE p.name = 'Azul' AND p.rescuer_id = 4;

INSERT INTO adoption_requests (pet_id, adopter_id, message, status, created_at)
SELECT p.id, 21, 'Tengo experiencia con perros grandes. ' || p.name || ' sería un gran compañero.', 'REJECTED', 1748736000000
FROM pets p WHERE p.name = 'Hércules' AND p.rescuer_id = 9;

-- -------------------------
-- EXTRA PETS (to reach 200 total)
-- Spread across rescuers 2-16, mostly dogs
-- -------------------------
INSERT INTO pets (rescuer_id, name, type, breed, description, weight, age_years, age_months, sex, status, color, size, temperament, is_sterilized, is_microchipped, vaccinations, is_good_with_kids, is_good_with_dogs, is_good_with_cats, is_house_trained, energy_level, rescue_date, rescue_location, adoption_fee, currency, is_urgent, is_promoted, created_at) VALUES
(2,'Pepper',   'DOG','Mestizo',               'Pepper tiene un carácter picante pero mucho amor.',5.0, 0,6,'FEMALE','AVAILABLE','Negro y blanco','SMALL','Vivaz',    false,false, 'Antirrábica',                         true,  false, true,  false, 'HIGH',   1749340800000,'Tepito, CDMX',            250.00,'MXN',true, false,1749340800000),
(2,'Goofy',    'DOG','Mestizo',               'Goofy siempre está metiendo la pata y es adorable.',9.0,1,4,'MALE','AVAILABLE','Café y blanco','SMALL','Torpe',     false,false, 'Antirrábica',                         true,  true,  true,  false, 'MEDIUM', 1750550400000,'Iztacalco, CDMX',         300.00,'MXN',false,false,1750550400000),
(3,'Samba',    'DOG','Labrador Retriever',    'Samba tiene ritmo y energía para bailar todo el día.',28.0,2,0,'FEMALE','AVAILABLE','Amarillo','LARGE','Rítmica',  false,false, 'Antirrábica, Moquillo',               true,  true,  true,  false, 'HIGH',   1748736000000,'Tlaquepaque, Jalisco',     800.00,'MXN',false,false,1748736000000),
(3,'Toby',     'DOG','Mestizo',               'Toby es simple, honesto y leal.',                 14.0,3,0,'MALE',  'AVAILABLE','Café',     'MEDIUM','Leal',       false,false, 'Antirrábica',                         true,  true,  false, true,  'LOW',    1749340800000,'Oblatos, Guadalajara',    300.00,'MXN',false,false,1749340800000),
(4,'Fuego',    'DOG','Mestizo',               'Fuego fue rescatado de una situación de abuso.',  20.0,4,0,'MALE',  'AVAILABLE','Rojo',     'LARGE', 'Sensible',  false,false, 'Antirrábica',                         false, false, false, false, 'LOW',    1748736000000,'Constitución, CABA',      0.00,'ARS', true, false,1748736000000),
(4,'Mia 2',    'DOG','Poodle',                'Mia adora el agua y los juguetes.',               5.2, 1,6,'FEMALE','AVAILABLE','Albaricoque','SMALL','Juguetona', false,false, 'Antirrábica',                         true,  false, true,  true,  'HIGH',   1749340800000,'Belgrano, CABA',          0.00,'ARS', false,false,1749340800000),
(5,'Macho',    'DOG','Rottweiler',            'Macho es grande y protector de su territorio.',   44.0,5,0,'MALE',  'AVAILABLE','Negro y café','LARGE','Protector', true, true,  'Antirrábica, Moquillo, Parvovirus',   false, false, false, true,  'MEDIUM', 1748736000000,'San Nicolás, NL',         750.00,'MXN',false,false,1748736000000),
(5,'Rosi',     'DOG','Mestizo',               'Rosi es la más popular del refugio.',              7.5, 2,0,'FEMALE','AVAILABLE','Rosada mezclada','SMALL','Popular',false,false,'Antirrábica',                         true,  true,  true,  true,  'MEDIUM', 1750550400000,'Monterrey Centro',        300.00,'MXN',false,true, 1750550400000),
(6,'Aldo',     'DOG','German Shepherd',       'Aldo fue rescatado de un circo. Muy inteligente.',38.0,6,0,'MALE',  'AVAILABLE','Negro y café','LARGE','Inteligente',true,true, 'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'MEDIUM', 1748736000000,'Santiago Centro',         0.00,'CLP', false,false,1748736000000),
(6,'Lua',      'DOG','Golden Retriever',      'Lua es la versión chilena de Luna.',               27.0,3,0,'FEMALE','AVAILABLE','Dorado',  'LARGE', 'Amorosa',   true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1750550400000,'Maipú, Santiago',         0.00,'CLP', false,false,1750550400000),
(7,'Rambo',    'DOG','Pitbull',               'Rambo tiene nombre de guerrero pero corazón de paloma.',30.0,4,0,'MALE','AVAILABLE','Azul gris','LARGE','Tierno',   true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  false, true,  'LOW',    1748736000000,'Laureles, Medellín',      0.00,'COP', false,false,1748736000000),
(7,'Susy',     'DOG','Mestizo',               'Susy llegó con tres cachorros y los crio sola.',  13.0,3,0,'FEMALE','AVAILABLE','Café oscuro','MEDIUM','Maternal', false,false, 'Antirrábica',                         true,  true,  true,  true,  'LOW',    1750550400000,'Envigado, Antioquia',     0.00,'COP', true, false,1750550400000),
(8,'Lenny',    'DOG','Labrador Retriever',    'Lenny ama el frisbee y ganar siempre.',           31.0,2,0,'MALE',  'AVAILABLE','Negro',    'LARGE', 'Atlético',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1748736000000,'Xochimilco, CDMX',        800.00,'MXN',false,false,1748736000000),
(8,'Perla',    'DOG','Mestizo',               'Perla es blanca y luminosa como su nombre.',       6.5, 1,0,'FEMALE','AVAILABLE','Blanco',  'SMALL', 'Luminosa',  false,false, 'Antirrábica',                         true,  false, true,  false, 'MEDIUM', 1750550400000,'Tláhuac, CDMX',           300.00,'MXN',false,false,1750550400000),
(9,'Ares',     'DOG','German Shepherd',       'Ares es el dios guardián del vecindario.',        36.0,3,0,'MALE',  'AVAILABLE','Negro',    'LARGE', 'Guardián',  true, true,  'Antirrábica, Moquillo, Parvovirus',   false, true,  false, true,  'HIGH',   1748736000000,'Estadio, Medellín',       0.00,'COP', false,false,1748736000000),
(9,'Salsa',    'DOG','Mestizo',               'Salsa tiene ritmo y siempre está bailando.',       9.5, 2,0,'FEMALE','AVAILABLE','Anaranjado','SMALL','Rítmica',  false,false, 'Antirrábica',                         true,  true,  true,  true,  'HIGH',   1750550400000,'La América, Medellín',    0.00,'COP', false,false,1750550400000),
(10,'Brisa',   'DOG','Mestizo',               'Brisa es fresca y ligera como el viento.',         7.0, 1,4,'FEMALE','AVAILABLE','Blanco y café','SMALL','Ligera',  false,false,'Antirrábica',                         true,  true,  true,  false, 'MEDIUM', 1748736000000,'Suba, Bogotá',            0.00,'COP', false,false,1748736000000),
(10,'Ronaldo', 'DOG','Labrador Retriever',    'Ronaldo anota goles con su nariz.',               29.5,2,0,'MALE',  'AVAILABLE','Amarillo', 'LARGE', 'Deportivo', false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1750550400000,'Fontibón, Bogotá',        0.00,'COP', false,false,1750550400000),
(11,'Chip',    'DOG','Chihuahua',             'Chip es tan pequeño como el chip de una computadora.',1.6,0,9,'MALE','AVAILABLE','Dorado', 'SMALL', 'Pequeño',   false,false, 'Antirrábica',                         false, false, false, false, 'HIGH',   1748736000000,'Apodaca, NL',             350.00,'MXN',false,false,1748736000000),
(11,'Boca',    'DOG','Mestizo',               'Boca siempre está ladrando pero es inofensivo.',  11.5,3,0,'MALE',  'AVAILABLE','Café',     'MEDIUM','Ruidoso',   false,false, 'Antirrábica',                         false, true,  false, true,  'HIGH',   1750550400000,'García, NL',              300.00,'MXN',false,false,1750550400000),
(12,'Pilar',   'DOG','Golden Retriever',      'Pilar es la columna del refugio, siempre ayuda.',  27.0,5,0,'FEMALE','AVAILABLE','Dorado', 'LARGE', 'Líder',     true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'LOW',    1748736000000,'San Isidro, Lima',        0.00,'PEN', false,false,1748736000000),
(12,'Willy',   'DOG','Mestizo',               'Willy es libre como la ballena del cuento.',      16.0,4,0,'MALE',  'AVAILABLE','Gris',     'MEDIUM','Libre',     false,false, 'Antirrábica',                         true,  true,  false, true,  'MEDIUM', 1750550400000,'Chorrillos, Lima',        0.00,'PEN', false,false,1750550400000),
(13,'Rocket',  'DOG','Border Collie',         'Rocket sale disparado cuando ve una pelota.',     19.0,1,6,'MALE',  'AVAILABLE','Negro y blanco','MEDIUM','Veloz', false,false, 'Antirrábica',                         true,  true,  true,  false, 'HIGH',   1748736000000,'Caballito, CABA',         0.00,'ARS', false,false,1748736000000),
(13,'Tanga',   'DOG','Mestizo',               'Tanga es tan flexible que parece acróbata.',      8.0, 2,0,'FEMALE','AVAILABLE','Tricolor', 'SMALL', 'Flexible',  false,false, 'Antirrábica',                         true,  true,  true,  true,  'MEDIUM', 1750550400000,'Flores, CABA',            0.00,'ARS', false,false,1750550400000),
(14,'Furia',   'DOG','Pitbull',               'Furia tiene nombre bravo pero es un bebé.',       25.0,2,0,'FEMALE','AVAILABLE','Café claro','LARGE','Dulce',     true, false, 'Antirrábica, Moquillo',               true,  true,  false, true,  'MEDIUM', 1748736000000,'Tonalá, Jalisco',         700.00,'MXN',false,false,1748736000000),
(14,'Pepón',   'DOG','Mestizo',               'Pepón es redondo y adorable.',                    20.0,4,0,'MALE',  'AVAILABLE','Naranja',  'MEDIUM','Adorable',  false,false, 'Antirrábica',                         true,  true,  true,  true,  'LOW',    1750550400000,'Guadalajara Centro',      300.00,'MXN',false,false,1750550400000),
(15,'Surf',    'DOG','Labrador Retriever',    'Surf ama el agua tanto como un surfista.',        29.0,2,0,'MALE',  'AVAILABLE','Amarillo', 'LARGE', 'Acuático',  true, false, 'Antirrábica, Moquillo',               true,  true,  true,  true,  'HIGH',   1748736000000,'Viña del Mar, Chile',     0.00,'CLP', false,false,1748736000000),
(15,'Kali',    'DOG','Mestizo',               'Kali es salvaje y libre como la diosa india.',    15.0,3,0,'FEMALE','AVAILABLE','Negro',   'MEDIUM','Libre',      false,false, 'Antirrábica',                         false, true,  false, false, 'HIGH',   1750550400000,'Pudahuel, Santiago',      0.00,'CLP', false,false,1750550400000),
(16,'Zara',    'DOG','Australian Shepherd',   'Zara tiene estilo de pasarela y mucha energía.',  20.0,2,0,'FEMALE','AVAILABLE','Merle azul','LARGE','Elegante',  true, true,  'Antirrábica, Moquillo, Parvovirus',   true,  true,  true,  true,  'HIGH',   1748736000000,'San Pedro, NL',           950.00,'MXN',false,true, 1748736000000),
(16,'Taco',    'DOG','Chihuahua',             'Taco cabe en tu mano y en tu corazón.',            1.9, 1,0,'MALE',  'AVAILABLE','Café',    'SMALL', 'Pequeño',   false,false, 'Antirrábica',                         false, false, false, false, 'HIGH',   1750550400000,'Monterrey Centro',        400.00,'MXN',false,false,1750550400000),
-- Extra cats and non-dogs for variety
(2,'Bigotes',  'CAT','Doméstico Pelo Largo',  'Bigotes tiene los bigotes más largos del refugio.',4.0,6,0,'MALE', 'AVAILABLE','Gris atigrado','SMALL','Sereno',  true, false, 'Triple Felina',                       false, false, true,  true,  'LOW',    1749340800000,'Coyoacán, CDMX',          400.00,'MXN',false,false,1749340800000),
(3,'Mittens',  'CAT','Ragdoll',               'Mittens tiene los pies blancos como guantes.',     5.5, 3,0,'FEMALE','AVAILABLE','Gris y blanco','SMALL','Dulce', true, false, 'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1749340800000,'Zapopan, Jalisco',        700.00,'MXN',false,false,1749340800000),
(4,'Felipa',   'CAT','Atigrada',              'Felipa es atigrada y muy independiente.',          3.5, 4,0,'FEMALE','AVAILABLE','Atigrado', 'SMALL','Independiente',false,false,'Triple Felina',                     false, false, true,  false, 'MEDIUM', 1749340800000,'Barracas, CABA',          0.00,'ARS', false,false,1749340800000),
(5,'Manchas',  'CAT','Doméstico Pelo Corto',  'Manchas tiene las manchas más creativas.',         3.2, 2,0,'MALE',  'AVAILABLE','Manchado', 'SMALL','Artístico',false,false, 'Triple Felina',                       false, false, true,  true,  'MEDIUM', 1749340800000,'Monterrey, NL',           350.00,'MXN',false,false,1749340800000),
(6,'Pelaje',   'CAT','Maine Coon',            'Pelaje tiene el pelo más largo de Chile.',         6.5, 5,0,'MALE',  'AVAILABLE','Atigrado pardo','SMALL','Majestuoso',true,false,'Triple Felina',                     false, false, true,  true,  'LOW',    1749340800000,'Providencia, Santiago',   0.00,'CLP', false,false,1749340800000),
(7,'Nube',     'CAT','Persa Blanco',          'Nube flota por la casa sin hacer ruido.',          4.8, 4,0,'FEMALE','AVAILABLE','Blanco',  'SMALL', 'Silenciosa',true, false, 'Triple Felina, Leucemia',             false, false, true,  true,  'LOW',    1749340800000,'El Centro, Medellín',     0.00,'COP', false,false,1749340800000),
(8,'Tornado',  'BIRD','Cacatúa Ninfa',        'Tornado silba todas las canciones que escucha.',   0.1, 3,0,'MALE',  'AVAILABLE','Gris y amarillo','SMALL','Músico',false,false,NULL,                                 false, false, false, true,  'MEDIUM', 1749340800000,'Polanco, CDMX',           350.00,'MXN',false,false,1749340800000),
(9,'Kakaroto', 'BIRD','Loro Verde',           'Kakaroto dice frases de su caricatura favorita.', 0.45,4,0,'MALE',  'AVAILABLE','Verde',   'SMALL', 'Héroe',     false,false, NULL,                                  false, false, false, true,  'LOW',    1749340800000,'Laureles, Medellín',      0.00,'COP', false,false,1749340800000),
(10,'Oscar',   'FISH','Oscar',                'Oscar es negro como la noche y muy territorial.', 0.15,1,0,'MALE',  'AVAILABLE','Negro',   'SMALL', 'Territorial',false,false,NULL,                                   false, false, false, true,  'MEDIUM', 1749340800000,'Bogotá, Colombia',        0.00,'COP', false,false,1749340800000),
(11,'Tetra',   'FISH','Neón Tetra',           'Tetra brilla en la oscuridad del acuario.',       0.002,0,2,'MALE', 'AVAILABLE','Azul neón', 'SMALL','Luminoso', false,false, NULL,                                  false, false, false, true,  'LOW',    1749340800000,'Monterrey, NL',            80.00,'MXN',false,false,1749340800000),
(12,'Burbuja', 'FISH','Goldfish Oranda',      'Burbuja tiene la cabeza más grande del acuario.',  0.06,1,0,'FEMALE','AVAILABLE','Naranja', 'SMALL', 'Cómica',   false,false, NULL,                                  false, false, false, true,  'LOW',    1749340800000,'Lima, Perú',              0.00,'PEN', false,false,1749340800000),
(13,'Milo',    'DOG','Mestizo',               'Milo fue encontrado en la puerta del refugio.',   13.0,1,0,'MALE',  'AVAILABLE','Café y blanco','MEDIUM','Misterioso',false,false,'Antirrábica',                          true,  true,  true,  false, 'MEDIUM', 1750550400000,'La Boca, CABA',           0.00,'ARS', true, false,1750550400000);

-- Backfill pets.country from currency: this seed data predates the mandatory
-- country-based search feature, so every row above left country NULL. Each
-- currency used here maps to exactly one country, so it's an unambiguous
-- backfill (unlike the free-text rescue_location column, which isn't).
UPDATE pets SET country = CASE currency
    WHEN 'MXN' THEN 'MEXICO'
    WHEN 'ARS' THEN 'ARGENTINA'
    WHEN 'COP' THEN 'COLOMBIA'
    WHEN 'CLP' THEN 'CHILE'
    WHEN 'PEN' THEN 'PERU'
END
WHERE country IS NULL;
