package dashboard;

import good.Good;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Header strip showing the headline market figures from the run. Pure layout —
 * each card is a heading + value {@link VBox} laid out in an {@link HBox}.
 */
final class SummaryCards {

    private SummaryCards() {}

    static HBox build() {
        HBox row = new HBox(8);
        row.setPadding(new Insets(8));

        row.getChildren().addAll(
                card("Funds raised", money(Good.getCompany().getFunds())),
                card("Starting price", money(Good.getStartingPrice())),
                card("Lowest", money(Good.getLowest())),
                card("Highest", money(Good.getHighest())),
                card("VWAP", money(Good.getVwap())),
                card("Total volume", String.format("%,.0f", Good.getVolume())),
                card("Trades", String.format("%,d", Good.getNumTrades()))
        );
        return row;
    }

    private static VBox card(String heading, String value) {
        Label h = new Label(heading);
        h.setFont(Font.font("SansSerif", FontWeight.NORMAL, 10));
        h.setStyle("-fx-text-fill: #666;");

        Label v = new Label(value);
        v.setFont(Font.font("SansSerif", FontWeight.BOLD, 16));

        VBox card = new VBox(2, h, v);
        card.setPadding(new Insets(8, 14, 8, 14));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 6;" +
                "-fx-border-color: #d8dae0;" +
                "-fx-border-radius: 6;");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private static String money(double v) {
        return String.format("£%,.2f", v);
    }
}
