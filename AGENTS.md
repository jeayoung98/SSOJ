# AGENTS.md

## Project overview
This project is an online judge platform.

- Next.js handles web pages and API routes.
- Spring Boot handles judge worker logic.
- PostgreSQL stores problems, test cases, submissions, and judge results.
- Redis is used as the queue between Next.js and the judge worker.
- Docker is used to compile and run untrusted user code in isolation.

## Current architecture
- Web/API: Next.js
- Judge worker: Spring Boot
- Queue: Redis List
- DB: PostgreSQL
- Sandbox: Docker

## Scope for this phase
This is a 3-day MVP.
Focus only on the minimum working judging pipeline.

Include:
- problem list/detail
- code submission
- async judging
- submission status/result
- submission history
- admin problem/testcase registration
- C++, Java, Python judging

Exclude:
- ranking
- plagiarism check
- multi-region deployment
- advanced sandbox hardening
- autoscaling
- SSE/realtime push

## Queue contract
- Redis key: `judge:queue`
- Payload: `submissionId`
- Next.js saves submission first, then pushes `submissionId` into Redis.
- Spring worker reads from Redis and performs judging.

## Submission status enum
- PENDING
- JUDGING
- AC
- WA
- CE
- RE
- TLE
- MLE
- SYSTEM_ERROR

## Core DB model
### Submission
- id: Long
- problemId: Long
- language: String
- sourceCode: Text
- status: Enum
- createdAt
- startedAt
- finishedAt

### Problem
- id: Long
- title
- description
- timeLimitMs
- memoryLimitMb

### TestCase
- id: Long
- problemId: Long
- input
- output
- isHidden

### SubmissionCaseResult
- id: Long
- submissionId: Long
- testCaseId: Long
- status
- executionTimeMs
- memoryUsageKb

## Judging policy
- Submission API must return quickly after enqueue.
- The worker must do actual judging asynchronously.
- Initial judging concurrency: 2
- Use Docker with:
    - no network
    - memory limit
    - cpu limit
    - timeout
- Clean up containers and temp files after execution.

## Language policy
### C++
- source file: main.cpp
- compile: g++ main.cpp -O2 -std=c++17 -o main
- run: ./main

### Java
- source file: Main.java
- compile: javac Main.java
- run: java Main
- assume user class name is Main for MVP

### Python
- source file: main.py
- run: python3 main.py

## Output comparison
- MVP policy: trim and compare line-by-line
- ignore trailing newline differences
- do not implement special judge

## Engineering rules
- Do not add Controller/Security/Swagger to the Spring worker
- Do not over-engineer
- Prefer simple, runnable code
- Keep changes minimal and scoped
- Before writing code, explain which files will be created/modified
- After coding, explain how to run and verify