package lab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** DPLL with either two watched literals or a full-scanner propagation reference. */
public final class Solver {
    public enum Mode { WATCHED, SCANNER }
    public enum Status { SAT, UNSAT, UNKNOWN }
    public record Event(int index, String kind, int level, int literal, int originalClause, int trailSize) {}
    public record Stats(long decisions, long implications, long conflicts, long backtracks,
                        long watchMoves, long propagationChecks, long satisfactionChecks,
                        long elapsedNs, long omittedTraceEvents) {}
    public record Result(Status status, int[] model, Stats stats, List<Event> trace) {}

    private final Formula formula;
    private final int[][] clauses;
    private final Mode mode;
    private final long budget;
    private final int[] values;
    private final int[] trail;
    private final int[] order;
    private final int[] first;
    private final int[] second;
    private final List<List<Integer>> watches = new ArrayList<>();
    private final List<Event> trace = new ArrayList<>();
    private int trailSize;
    private int propagated;
    private long decisions, implications, conflicts, backtracks, watchMoves;
    private long propagationChecks, satisfactionChecks, omitted;
    private boolean solved;

    public Solver(Formula formula, Mode mode, long decisionBudget) {
        if (formula == null || mode == null || decisionBudget < 0 || decisionBudget > 1_000_000) {
            throw new IllegalArgumentException("Formula, mode and bounded budget required");
        }
        this.formula = formula;
        this.mode = mode;
        this.budget = decisionBudget;
        clauses = formula.clauses();
        values = new int[formula.variables() + 1];
        trail = new int[formula.variables()];
        first = new int[clauses.length];
        second = new int[clauses.length];
        int[] frequency = new int[values.length];
        for (int[] clause : clauses) for (int literal : clause) frequency[Math.abs(literal)]++;
        Integer[] boxed = new Integer[formula.variables()];
        for (int i = 0; i < boxed.length; i++) boxed[i] = i + 1;
        Arrays.sort(boxed, Comparator.<Integer>comparingInt(v -> -frequency[v]).thenComparingInt(v -> v));
        order = Arrays.stream(boxed).mapToInt(Integer::intValue).toArray();
        for (int i = 0; i < formula.variables() * 2; i++) watches.add(new ArrayList<>());
    }

    private int literalValue(int literal) {
        return literal > 0 ? values[literal] : -values[-literal];
    }

    private int checkedValue(int literal) {
        propagationChecks++;
        return literalValue(literal);
    }

    private static int index(int literal) {
        return (Math.abs(literal) - 1) * 2 + (literal < 0 ? 1 : 0);
    }

    private void event(String kind, int level, int literal, int clause) {
        if (trace.size() < 2000) {
            trace.add(new Event(trace.size(), kind, level, literal,
                clause < 0 ? -1 : formula.originalId(clause), trailSize));
        } else {
            omitted++;
        }
    }

    private boolean assign(int literal, int level, int clause) {
        int current = literalValue(literal);
        if (current == 1) return true;
        if (current == -1) return false;
        values[Math.abs(literal)] = literal > 0 ? 1 : -1;
        trail[trailSize++] = literal;
        if (clause >= 0) {
            implications++;
            event("IMPLY", level, literal, clause);
        }
        return true;
    }

    private boolean conflict(int clause, int level) {
        conflicts++;
        event("CONFLICT", level, 0, clause);
        return false;
    }

    private boolean initialize() {
        for (int clause = 0; clause < clauses.length; clause++) {
            int[] literals = clauses[clause];
            if (literals.length == 0) return conflict(clause, 0);
            second[clause] = literals.length > 1 ? 1 : 0;
            if (mode == Mode.WATCHED) {
                watches.get(index(literals[0])).add(clause);
                if (literals.length > 1) watches.get(index(literals[1])).add(clause);
            }
            if (literals.length == 1 && !assign(literals[0], 0, clause)) return conflict(clause, 0);
        }
        return true;
    }

    private boolean propagateWatched(int level) {
        while (propagated < trailSize) {
            int falseLiteral = -trail[propagated++];
            List<Integer> affected = watches.get(index(falseLiteral));
            int cursor = 0;
            while (cursor < affected.size()) {
                int clause = affected.get(cursor);
                int[] literals = clauses[clause];
                boolean movingFirst = literals[first[clause]] == falseLiteral;
                int otherPosition = movingFirst ? second[clause] : first[clause];
                int otherLiteral = literals[otherPosition];
                if (checkedValue(otherLiteral) == 1) {
                    cursor++;
                    continue;
                }
                int replacement = -1;
                for (int position = 0; position < literals.length; position++) {
                    if (position == first[clause] || position == second[clause]) continue;
                    if (checkedValue(literals[position]) != -1) {
                        replacement = position;
                        break;
                    }
                }
                if (replacement >= 0) {
                    if (movingFirst) first[clause] = replacement;
                    else second[clause] = replacement;
                    int last = affected.size() - 1;
                    affected.set(cursor, affected.get(last));
                    affected.remove(last);
                    watches.get(index(literals[replacement])).add(clause);
                    watchMoves++;
                    event("WATCH_MOVE", level, literals[replacement], clause);
                    // Swap removal means a new affected clause occupies this cursor.
                } else {
                    if (checkedValue(otherLiteral) == -1) return conflict(clause, level);
                    if (!assign(otherLiteral, level, clause)) return conflict(clause, level);
                    cursor++;
                }
            }
        }
        return true;
    }

    private boolean propagateScanner(int level) {
        boolean changed;
        do {
            changed = false;
            for (int clause = 0; clause < clauses.length; clause++) {
                int unassigned = 0;
                int remaining = 0;
                boolean satisfied = false;
                for (int literal : clauses[clause]) {
                    int value = checkedValue(literal);
                    if (value == 1) { satisfied = true; break; }
                    if (value == 0) { unassigned++; remaining = literal; }
                }
                if (satisfied) continue;
                if (unassigned == 0) return conflict(clause, level);
                if (unassigned == 1) {
                    if (!assign(remaining, level, clause)) return conflict(clause, level);
                    changed = true;
                }
            }
        } while (changed);
        propagated = trailSize;
        return true;
    }

    private boolean satisfied() {
        for (int[] clause : clauses) {
            boolean satisfied = false;
            for (int literal : clause) {
                satisfactionChecks++;
                if (literalValue(literal) == 1) { satisfied = true; break; }
            }
            if (!satisfied) return false;
        }
        return true;
    }

    private void backtrack(int size, int level) {
        while (trailSize > size) values[Math.abs(trail[--trailSize])] = 0;
        propagated = Math.min(propagated, size);
        backtracks++;
        event("BACKTRACK", level, 0, -1);
        // Watches remain valid after unassignment; no watch-position undo is needed.
    }

    private Status search(int level) {
        boolean consistent = mode == Mode.WATCHED ? propagateWatched(level) : propagateScanner(level);
        if (!consistent) return Status.UNSAT;
        if (satisfied()) return Status.SAT;
        int variable = 0;
        for (int candidate : order) if (values[candidate] == 0) { variable = candidate; break; }
        if (variable == 0) throw new IllegalStateException("Unsatisfied complete assignment escaped propagation");
        int checkpoint = trailSize;
        for (int literal : new int[] {variable, -variable}) {
            if (decisions == budget) return Status.UNKNOWN;
            decisions++;
            assign(literal, level + 1, -1);
            event("DECIDE", level + 1, literal, -1);
            Status result = search(level + 1);
            if (result == Status.SAT) return result;
            backtrack(checkpoint, level);
            if (result == Status.UNKNOWN) return result;
        }
        return Status.UNSAT;
    }

    public Result solve() {
        if (solved) throw new IllegalStateException("Solver instances are single use");
        solved = true;
        long started = System.nanoTime();
        Status status = initialize() ? search(0) : Status.UNSAT;
        int[] model = null;
        if (status == Status.SAT) {
            model = new int[formula.variables()];
            for (int v = 1; v < values.length; v++) model[v - 1] = values[v] == 1 ? v : -v;
            if (!formula.verifies(model)) throw new IllegalStateException("Invalid SAT model");
        }
        event(status.name(), 0, 0, -1);
        long elapsed = System.nanoTime() - started;
        return new Result(status, model, new Stats(decisions, implications, conflicts, backtracks,
            watchMoves, propagationChecks, satisfactionChecks, elapsed, omitted), List.copyOf(trace));
    }
}
