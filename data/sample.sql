create table einsatzmittel
(
    RUFNAME                varchar(30)                          not null comment 'Rufname des Einsatzmittels, eindeutige Bezeichnung'
        primary key,
    RUFNAMELANG            varchar(60)                          null comment 'Optionaler Rufname lang',
    FMSKENNUNG             varchar(8)                           null comment 'FMS-Kennung im Analogfunk (ohne Relevanz im Digitalfunk)',
    KANAL                  varchar(11)                          null comment 'Kanal des EMs bei Statusauswertung im Analogfunk (ohne Relevanz im Digitalfunk)',
    STATUS                 char     default '2'                 not null,
    TKI                    smallint default 1                   null,
    ZEITSTEMPEL            datetime default current_timestamp() null,
    NOTRUF                 smallint default 0                   null comment '1= True
0= False',
    SPRECHWUNSCH           smallint default 0                   null comment '1= True
0= False',
    TYP                    varchar(20)                          null comment 'Fahrzeugtyp',
    MANUELL                smallint default 0                   null,
    STANDORT               varchar(100)                         null,
    WACHE                  varchar(100)                         null,
    AAOTYP                 varchar(4)                           null,
    ZUSATZ                 varchar(200)                         null,
    EINSATZ                varchar(20)                          null comment 'Einsatznummer des Einsatzes, in dem das EM aktiv ist. So lange dieses Feld einen Wert hat, kann das EM keinem anderen Einsatz zugeteilt werden',
    EINSATZNUMMER          varchar(20)                          null comment 'Einsatznummer des Einsatzes, in dem das EM aktiv ist. Das Feld behÃ¤lt den Wert bis zur Zuteilung in einen neuen Einsatz oder bis zum Status 2. Im Gegensatz zum Feld EINSATZ ist das Feld noch mit der Einsatznummer befÃ¼llt, wenn das EM im Status 1 auf der RÃ¼ckfahrt ist',
    PERSONAL               varchar(300)                         null,
    ABSCHNITT              varchar(150)                         null comment 'ID des Einsatzabschnitts aus der Tabelle EINSATZABSCHNITTE',
    AUFTRAG                varchar(300)                         null,
    BESATZUNG_0            int      default 0                   null comment 'Besatzung VF',
    BESATZUNG_1            int      default 0                   null comment 'Besatzung ZF',
    BESATZUNG_2            int      default 0                   null comment 'Besatzung GF',
    BESATZUNG_3            int      default 0                   null comment 'Besatzung Mannschaft',
    BESATZUNG_GES          int      default 0                   null comment 'GesamtstÃ¤rke Mannschaft',
    ABSCHNITTZEITSTEMPEL   datetime                             null,
    FREMDFAHRZEUG          smallint default 0                   null comment '0 = RegulÃ¤r, 1=Fremdfahrzeug',
    VORDISPONIERT          int                                  null comment 'Einsatznummer des Einsatzes, fÃ¼r den das EM vordisponiert ist',
    S6_GRUND               varchar(100)                         null comment 'Grund weshalb das EM im Status 6 steht',
    S6_TIME                datetime                             null comment 'Uhrzeit bis wann das EM auÃŸer Dienst ist',
    BESATZUNG_PA           int      default 0                   null comment 'BesatzungsstÃ¤rke der PA-TrÃ¤ger',
    KOORDX                 double                               null comment 'Koordinate (X-Wert/LÃ¤nge)',
    KOORDY                 double                               null comment 'Koordinate (Y-Wert/Breite)',
    TAKTZEICHEN            varchar(1024)                        null comment 'Taktisches Zeichen des EMs',
    LASTGEOPOS             datetime                             null,
    GPS_PRECISION          double                               null,
    GPS_SPEED              double                               null,
    GPS_HEADING            double                               null,
    SPRECHWUNSCH_TIMESTAMP datetime                             null,
    LASTGEOUSER            varchar(20)                          null,
    GPS_HEIGHT             double                               null,
    DISPOSITION            smallint default 1                   null comment 'Legt fest, ob das EM in Verwendung ist und SprechwÃ¼nsche ausgewertet werden sollen',
    ABSCHNITT_ID           int                                  null comment 'ID des Einsatzabschnitts aus der zentralen Abschnittsverwaltung',
    EDPCUSER               varchar(50)                          null,
    TELEFONNUMMER          varchar(50)                          null,
    EXTERNAL_ID            varchar(100)                         null comment 'Kennung des Einsatzmittels in Fremdsystemen zur Nutzung von Leitstellen-Schnittstellen (z.B. KEZ-Schnittstelle, WDX3-Schnittstelle)'
)
    comment 'Tabelle der Einsatzmittel';


CREATE TABLE IF NOT EXISTS webhook_outbox (
                                id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                event_type ENUM('INSERT','UPDATE','DELETE') NOT NULL,
                                table_name VARCHAR(64) NOT NULL,
                                pk VARCHAR(255) NOT NULL,
                                payload JSON NOT NULL,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                status ENUM('NEW','SENDING','SENT','FAILED') NOT NULL DEFAULT 'NEW',
                                attempts INT NOT NULL DEFAULT 0,
                                next_retry_at TIMESTAMP NULL DEFAULT NULL,
                                last_error TEXT NULL,
                                sent_at TIMESTAMP NULL DEFAULT NULL,
                                correlation_id CHAR(36) NULL
);
CREATE INDEX idx_outbox_status_retry ON webhook_outbox(status, next_retry_at, id);


CREATE TRIGGER einsatzmittel_webhook_au AFTER UPDATE ON einsatzmittel
    FOR EACH ROW
BEGIN
    IF OLD.STATUS <> NEW.STATUS THEN
        INSERT INTO webhook_outbox(event_type, table_name, pk, payload, correlation_id)
        VALUES (
                   'UPDATE', 'einsatzmittel', CAST(OLD.RUFNAME AS CHAR),
                   JSON_OBJECT('OLD_STATUS', OLD.STATUS, 'NEW_STATUS', NEW.STATUS),
                   UUID()
               );
    END IF;
END;


insert into webhook_outbox (id, event_type, table_name, pk, payload, created_at, status, attempts, next_retry_at, last_error, sent_at, correlation_id) values (1, 'UPDATE', 'einsatzmittel', 'BN EAL Innenstadt', '{"RUFNAME": "BN EAL Innenstadt"}', '2025-12-30 22:48:23', 'NEW', 0, null, null, null, '40424816-e5c9-11f0-9c3f-54bf649d29d4');
insert into webhook_outbox (id, event_type, table_name, pk, payload, created_at, status, attempts, next_retry_at, last_error, sent_at, correlation_id) values (2, 'UPDATE', 'einsatzmittel', 'BN EAL Innenstadt', '{"RUFNAME": "BN EAL Innenstadt"}', '2025-12-30 22:48:30', 'NEW', 0, null, null, null, '44e7df2e-e5c9-11f0-9c3f-54bf649d29d4');
