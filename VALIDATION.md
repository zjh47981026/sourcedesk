# Validation record — October 3, 2026

## Automated checks

- Maven test package: 19 tests, 0 failures, 0 errors, 0 skipped on the final implementation.
- JavaScript and launcher syntax checks passed.
- JUnit covers PDF physical pages, upload validation, workspace/session isolation, selected-document retrieval, unavailable inference and citation contracts, including combined labels and invalid references.
- A GitHub Actions workflow builds and tests on Java 17. Its remote result is separate from these local checks.

## Real local-model evaluation

Ollama 0.35.1, qwen3:4b (tag digest 359d7dd4bcda), cloud features disabled, loopback endpoint, no personal documents. Twelve synthetic fixtures were run twice against the final application: **22/24 fixture checks passed**. Every actual response is retained in evaluation/results.json; rerun with python3 evaluation/run_eval.py --repeats 2 while the app and model are running.

Both failures were false abstentions: the model declined the supported parental-leave question instead of answering 12 weeks with a physical page-2 citation. Remaining checks covered annual leave, learning budget, hotel/meal allowance, remote work, approval threshold, missing evidence, unsupported contractor eligibility, document selection, conflicting policies and one embedded malicious instruction. For conflicts, abstaining counts as a safe fixture outcome; this does not demonstrate conflict explanation. Numeric checks are intentionally narrow, not a semantic accuracy benchmark or comprehensive injection defense.

Observed nonzero response times: 2.8–20.8 seconds; median 7.4 seconds on this Mac. Measurements include app overhead and some model loading; they are not isolated performance benchmarks.

## Live API and browser checks

- Fictional documents load; a real generated annual-leave answer is inspectable with its source excerpt.
- Unexpected Host and cross-origin mutations return HTTP 403.
- Chrome rendered empty state, selected documents, answer cards and source drawer. A 390 × 844 mobile viewport was reviewed and reset.
- The hosted showcase is a separate static fixture demo. It sends no document uploads or inference requests.

## Independent advice

Chrome Muse reviewed architecture/retrieval. Safari Muse reviewed UX. Local Muse reviewed failure cases/security. Safari ChatGPT Pro independently critiqued the specification. They supplied advice, not implementation certification; the recorded tests establish what was executed.

## Remaining limits

No comprehensive hostile-PDF deadline test, accessibility audit, load test or offline traffic monitoring. Citation validity does not establish semantic support. BM25 paraphrase recall and small-model false abstentions remain limitations. The app has no durable storage or production authentication and must remain local.
