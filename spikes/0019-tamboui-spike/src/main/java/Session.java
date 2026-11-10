// THROWAWAY spike for issue 0019. Plain logic, no TamboUI types. Shows how Session logic stays outside the glue.
import java.util.ArrayList;
import java.util.List;

final class Session {
    enum Kind { INPUT, RESULT, INFO, ERROR }
    record Band(Kind kind, String text) {}
    enum Action { NONE, QUIT, WORK, CRASH, COPY }

    final List<Band> bands = new ArrayList<>();

    Session() {
        bands.add(new Band(Kind.INFO, "dbui spike 0019. Type text and press Enter. /work, /out N, /copy, /crash, /quit."));
        for (int i = 1; i <= 300; i++) {
            bands.add(new Band(Kind.INPUT, "select * from t" + i + "\nwhere id = " + i));
            bands.add(new Band(Kind.RESULT, "id | name\n" + i + "  | row " + i));
            if (i % 50 == 0) bands.add(new Band(Kind.INFO, "info band " + i));
            if (i % 70 == 0) bands.add(new Band(Kind.ERROR, "error band " + i + ": relation does not exist"));
        }
    }

    Action submit(String text) {
        String t = text.strip();
        if (t.isEmpty()) return Action.NONE;
        bands.add(new Band(Kind.INPUT, text));
        switch (t.split("\\s+")[0]) {
            case "/quit": return Action.QUIT;
            case "/work": return Action.WORK;
            case "/crash": return Action.CRASH;
            case "/copy": return Action.COPY;
            case "/out":
                int n = t.split("\\s+").length > 1 ? Integer.parseInt(t.split("\\s+")[1]) : 10;
                for (int i = 0; i < n; i++) bands.add(new Band(Kind.RESULT, "out " + i));
                return Action.NONE;
            default:
                bands.add(new Band(Kind.RESULT, "result for: " + t.replace('\n', ' ')));
                return Action.NONE;
        }
    }

    String lastResult() {
        for (int i = bands.size() - 1; i >= 0; i--) if (bands.get(i).kind() == Kind.RESULT) return bands.get(i).text();
        return "";
    }
}
