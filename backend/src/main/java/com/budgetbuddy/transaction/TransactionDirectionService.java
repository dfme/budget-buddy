package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.TransactionResponse;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buchungen mit ungeprüfter Richtung lesen und korrigieren (BE-PDF-10, US-04).
 *
 * <p>Der Parser leitet die Buchungsrichtung aus dem Saldo ab. Wo das nicht gelingt, übernimmt er
 * die Buchung konservativ als Belastung und markiert sie
 * ({@link Transaction#isDirectionUncertain()}). Bis BE-PDF-10 endete die Geschichte dort: Die
 * Warnung ging ins Log, die Oberfläche zeigte die Buchung wie jede andere. Ist eine Gutschrift
 * darunter, ist ihr Vorzeichen gedreht — der Betrag wirkt sich damit doppelt aus und
 * Safe-to-Spend fällt zu tief aus.
 *
 * <p>Dieser Service schliesst den Kreis: Er liefert die offenen Fälle eines Monats und nimmt die
 * Entscheidung des Nutzers entgegen. Aufgebaut wie {@link TransactionCategoryService}, mit dem er
 * sich das Muster der manuellen Korrektur aus US-05 teilt — mit einem Unterschied: Es wird nichts
 * gelernt. Eine Kategorie hängt am Händler und gilt beim nächsten Mal wieder; die Richtung hängt
 * am Saldo-Kontext einer einzelnen Buchung, aus dem sich für die nächste nichts ableiten lässt.
 *
 * <p><strong>Mandantentrennung:</strong> Beide Methoden lesen ausschliesslich über
 * {@code userId}-gebundene Repository-Aufrufe. Die Einschränkung steht hier und nicht erst im
 * Controller — sonst wäre sie von einem zweiten Aufrufer aus umgehbar.
 */
@Service
public class TransactionDirectionService {

    private final TransactionRepository transactionRepository;

    public TransactionDirectionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Die Buchungen des Monats, deren Richtung der Parser nur angenommen hat — neueste zuerst.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param month Monat im Format {@code YYYY-MM}.
     * @return die offenen Fälle; leere Liste, wenn der Monat keine enthält — der Normalfall.
     * @throws InvalidMonthException wenn {@code month} fehlt oder kein {@code YYYY-MM} ist.
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> listUncertain(long userId, String month) {
        YearMonth yearMonth = MonthParser.parse(month);
        return transactionRepository
                .findByUserIdAndDirectionUncertainTrueAndBuchungsdatumBetweenOrderByBuchungsdatumDescIdDesc(
                        userId, yearMonth.atDay(1), yearMonth.atEndOfMonth())
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /**
     * Übernimmt die vom Nutzer entschiedene Buchungsrichtung.
     *
     * <p>Gilt für jede Transaktion des Users, nicht nur für die markierten: Auch eine Richtung, die
     * der Parser sicher bestimmt hat, kann falsch sein — etwa wenn ein Layout künftig
     * fehlinterpretiert wird. Ein zusätzlicher Vorab-Check auf das Flag würde diesen Fall
     * blockieren, ohne dafür etwas zu schützen; die Wirkung ist dieselbe wie beim markierten Fall.
     *
     * <p>Die Wirkung auf Safe-to-Spend ergibt sich von selbst und braucht hier nichts:
     * {@code MonthlyExpensePort.expenseAmounts} wählt über {@code is_income = false}, eine auf
     * Gutschrift gesetzte Buchung fällt damit aus dem Ausgaben-Summanden.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param transactionId ID der zu ändernden Transaktion.
     * @param income {@code true} für Gutschrift, {@code false} für Belastung.
     * @return die aktualisierte Transaktion — mit abgeräumtem {@code directionUncertain}.
     * @throws TransactionNotFoundException wenn keine Transaktion mit dieser ID dem User gehört.
     */
    @Transactional
    public TransactionResponse updateDirection(long userId, long transactionId, boolean income) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(tx -> tx.getUserId() == userId)
                .orElseThrow(() -> new TransactionNotFoundException(userId, transactionId));

        // Eine Methode statt zweier Setter: Richtung setzen und Flag abräumen gehören zusammen,
        // auch wenn der Nutzer die angenommene Richtung bloss bestätigt (siehe Transaction).
        transaction.correctDirection(income);
        transactionRepository.save(transaction);

        return TransactionResponse.from(transaction);
    }
}
