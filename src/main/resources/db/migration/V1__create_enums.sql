CREATE TYPE notification_status AS ENUM ('PENDING', 'DELIVERED', 'FAILED', 'CANCELLED');
CREATE TYPE notification_channel AS ENUM ('EMAIL', 'SMS', 'PUSH');
