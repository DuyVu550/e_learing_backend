ALTER TABLE lessons
    ADD COLUMN content_type VARCHAR(30) NOT NULL DEFAULT 'TEXT' AFTER title,
    ADD COLUMN document_url VARCHAR(500) NULL AFTER video_url;
