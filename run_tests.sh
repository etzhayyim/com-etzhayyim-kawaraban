#!/usr/bin/env bash
# kawaraban 瓦版 — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec bb -e '(require (quote clojure.test) (quote kawaraban.cells.test-state-machine) (quote kawaraban.methods.test-analyze) (quote kawaraban.methods.test-charter-gates) (quote kawaraban.methods.test-ingest) (quote kawaraban.methods.test-route))(let [r (clojure.test/run-tests (quote kawaraban.cells.test-state-machine) (quote kawaraban.methods.test-analyze) (quote kawaraban.methods.test-charter-gates) (quote kawaraban.methods.test-ingest) (quote kawaraban.methods.test-route))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
