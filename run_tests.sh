#!/usr/bin/env bash
# kawaraban 瓦版 — bb/clj test suite (ADR-2606160842 py→clj port wave; Python pruned).
# ADR-2607110200: fixed cwd (repo root, where bb.edn + the self-referential `kawaraban ->
# .` symlink live -- the symlink lets the `kawaraban.*`-prefixed namespaces resolve against
# this repo's flat cells/methods/ layout without renaming any existing file) and added
# methods.test-live-fetch (R0->R1 live RSS/Atom fetch scaffold).
set -euo pipefail
cd "$(dirname "$0")"
exec bb -e '(require (quote clojure.test) (quote kawaraban.cells.test-state-machine) (quote kawaraban.methods.test-analyze) (quote kawaraban.methods.test-charter-gates) (quote kawaraban.methods.test-ingest) (quote kawaraban.methods.test-live-fetch) (quote kawaraban.methods.test-route))(let [r (clojure.test/run-tests (quote kawaraban.cells.test-state-machine) (quote kawaraban.methods.test-analyze) (quote kawaraban.methods.test-charter-gates) (quote kawaraban.methods.test-ingest) (quote kawaraban.methods.test-live-fetch) (quote kawaraban.methods.test-route))](System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))'
