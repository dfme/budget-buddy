-- FE-NOTIF-04 (#336): Abo-Benachrichtigungen werden pro Erkennungslauf gebündelt — eine
-- Notification «7 neue Abos erkannt: …» statt sieben. Damit zeigt notifications.reference_id
-- (V10) nicht mehr auf eine recurring_expenses-Zeile; der Verweis dreht sich um und wandert auf
-- die N-Seite: jede Zeile kennt die Bündel-Notification, aus deren Gelesen-Zustand ihr
-- «Neu»-Flag abgeleitet wird (RecurringExpenseService.list, BE-REC-02).
--
-- Kein FK, aus demselben Grund wie reference_id in V10 gespiegelt: die Kontolöschung (US-02)
-- räumt beide Tabellen über eigene Cleanup-Ports auf, und ein FK machte deren Reihenfolge zur
-- versteckten Vorbedingung. Eine verwaiste notification_id ist harmlos — die Zeile gilt dann
-- schlicht nicht mehr als neu.
--
-- NULL bleibt erlaubt: Zeilen ohne Bündel (nach dem Backfill nur solche, deren
-- Einzel-Notification bereits gelöscht war) sind nie «Neu».
ALTER TABLE recurring_expenses ADD COLUMN notification_id BIGINT;

-- Backfill für Bestandsdaten: Bis hierher lag pro Zeile genau eine Notification mit
-- reference_id = recurring_expenses.id (BE-REC-01). Sie wird zum «Bündel der Grösse 1», damit
-- ein bereits erkanntes, noch ungelesenes Abo sein «Neu» durch diese Migration nicht verliert.
-- MIN(id) nur als Absicherung — V11 und BE-REC-01 liessen nie mehr als eine Notification pro
-- Zeile zu.
UPDATE recurring_expenses re
   SET notification_id = (
       SELECT MIN(n.id)
         FROM notifications n
        WHERE n.user_id = re.user_id
          AND n.type = 'RECURRING_EXPENSE_DETECTED'
          AND n.reference_id = re.id
   );

-- Kein zusätzlicher Index: die einzige Query auf die Spalte
-- (RecurringExpenseRepository.findByUserIdAndNotificationId) führt mit user_id und wird vom
-- UNIQUE (user_id, payee_key) aus V11 über dessen Präfix bedient — die Bündel eines Users sind
-- klein.
