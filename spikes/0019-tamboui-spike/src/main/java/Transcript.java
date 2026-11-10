// THROWAWAY spike for issue 0019. Plain logic: flattens bands to screen rows and keeps the scroll offset.
import java.util.ArrayList;
import java.util.List;

final class Transcript {
    record Row(Session.Kind kind, String text) {} // text == null: padding row (blank, band background)

    int scroll; // rows above the bottom; 0 = follow newest

    List<Row> rows(List<Session.Band> bands, int width) {
        List<Row> out = new ArrayList<>();
        int inner = Math.max(1, width - 2);
        for (Session.Band b : bands) {
            boolean padded = b.kind() != Session.Kind.RESULT;
            if (padded) out.add(new Row(b.kind(), null));
            for (String line : b.text().split("\n", -1)) {
                do {
                    int n = Math.min(inner, line.length());
                    out.add(new Row(b.kind(), line.substring(0, n)));
                    line = line.substring(n);
                } while (!line.isEmpty());
            }
            if (padded) out.add(new Row(b.kind(), null));
        }
        return out;
    }

    void by(int rows, int total, int view) {
        scroll = Math.max(0, Math.min(Math.max(0, total - view), scroll + rows));
    }
}
