# CodeVigil

A Clojure backend service for GitHub repository pull request analysis.

## Overview

CodeVigil is a backend service that:
- Accepts GitHub repository URLs via an API endpoint
- Fetches pull requests from the last 4 weeks
- Enriches pull requests with reviews, review comments, and requested reviewers
- Stores all data in a PostgreSQL database

## Requirements

- Docker and Docker Compose
- GitHub Personal Access Token with `repo` scope

## Setup

1. Clone this repository
2. Create a `.env` file from the example:
   ```
   cp .env.example .env
   ```
3. Edit the `.env` file and add your GitHub token and database password:
   ```
   GITHUB_TOKEN=your_github_token_here
   DB_PASSWORD=your_secure_password_here
   ```
4. Build and start the services:
   ```
   docker-compose up -d
   ```

## API Usage

### Register a Repository

```
POST /api/repositories
Content-Type: application/json

{
  "repo_url": "https://github.com/owner/repo"
}
```

Response:
```json
{
  "status": "success",
  "repository": {
    "owner": "owner",
    "repo": "repo",
    "url": "https://github.com/owner/repo"
  },
  "pull_requests_count": 5
}
```

### Get Aggregated Metrics

Retrieve aggregated metrics for a repository over time (weekly or daily).

```
GET /api/metrics/{owner}/{repo}?aggregation={weekly|daily}
```

Example:
```
GET /api/metrics/Helicone/helicone?aggregation=weekly
```

Response:
```json
{
  "status": "success",
  "repository": {
    "owner": "Helicone",
    "repo": "helicone"
  },
  "aggregation": "weekly",
  "metrics": [
    {
      "total_prs": 29,
      "reviewed_prs": 29,
      "median_merge_time_seconds": 505,
      "merged_prs": 29,
      "median_merge_time": "8 minutes, 25 seconds",
      "end_date": "2025-03-24T00:00-04:00[America/Toronto]",
      "start_date": "2025-03-17T00:00-04:00[America/Toronto]",
      "period": "This week",
      "median_review_turnaround_seconds": 374,
      "median_review_turnaround": "6 minutes, 14 seconds"
    },
    {
      "total_prs": 45,
      "reviewed_prs": 45,
      "median_merge_time_seconds": 1150,
      "merged_prs": 37,
      "median_merge_time": "19 minutes, 10 seconds",
      "end_date": "2025-03-17T00:00-04:00[America/Toronto]",
      "start_date": "2025-03-10T00:00-04:00[America/Toronto]",
      "period": "1 week ago",
      "median_review_turnaround_seconds": 3878,
      "median_review_turnaround": "1 hours, 4 minutes"
    },
    // ... additional weeks ...
  ]
}
```

#### Available Metrics

| Metric | Description |
|--------|-------------|
| total_prs | Total number of PRs in the period |
| reviewed_prs | Number of PRs that received at least one review |
| merged_prs | Number of PRs that were merged |
| median_merge_time | Median time from PR creation to merge (formatted) |
| median_merge_time_seconds | Median time from PR creation to merge (in seconds) |
| median_review_turnaround | Median time from PR creation to first review (formatted) |
| median_review_turnaround_seconds | Median time from PR creation to first review (in seconds) |
| start_date | Start date of the period |
| end_date | End date of the period |
| period | Human-readable description of the period |

## Development

### Prerequisites

- JDK 17+
- Leiningen
- PostgreSQL

### Running Locally

1. Set environment variables:
   ```
   export GITHUB_TOKEN=your_github_token_here
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=codevigil
   export DB_USER=postgres
   export DB_PASSWORD=your_password_here
   ```

2. Start the application:
   ```
   lein run
   ```

3. The API will be available at http://localhost:3000
   - Swagger UI: http://localhost:3000/swagger-ui

## Database Schema

- **repositories**: Stores repository information
  - id (PK)
  - owner
  - repo_name
  - repo_url
  - created_at
  - last_checked_at

- **pull_requests**: Stores pull request data
  - id (PK)
  - repository_id (FK)
  - github_id
  - number
  - state
  - title
  - html_url
  - created_at
  - updated_at
  - closed_at
  - merged_at
  - user_login

- **reviews**: Stores pull request reviews
  - id (PK)
  - pull_request_id (FK)
  - github_id
  - user_login
  - state
  - submitted_at
  - commit_id

- **review_comments**: Stores comments on pull request reviews
  - id (PK)
  - pull_request_id (FK)
  - github_id
  - user_login
  - created_at
  - updated_at
  - commit_id
  - path
  - position
  - body

- **requested_reviewers**: Stores users requested to review a pull request
  - id (PK)
  - pull_request_id (FK)
  - user_login

- **metrics**: Stores calculated metrics for pull requests
  - id (PK)
  - repository_id (FK)
  - pull_request_id (FK)
  - pr_number
  - pr_merge_time_seconds
  - code_review_turnaround_seconds
  - first_review_requested_at
  - first_approval_at
  - calculated_at

## Testing

Run the tests with:
```
lein test
```

## License

Copyright © 2025
