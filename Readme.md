# AdBot (Playtech PvP Simulation)

Java bot for the Playtech ad-auction simulator.

The bot reads impression lines from stdin and outputs bids in format:

`startBid maxBid`

Goal: maximize score (points per ebuck) under budget constraints.

## Current Bot Theme

- Default category: `Sports`
- Optional override via CLI: second argument can set any category

## Strategy Overview

The bot combines:

- Weighted impression value model
- Epsilon-greedy arm selection for bid multiplier
- Competition-aware shading (low/medium/high)
- ROI gate to skip weak auctions
- Budget pacing to avoid spending too early
- Recovery path when the latest 100-round window had zero spend

Main value signals:

- Engagement (`comments / views`)
- Interest/category affinity
- Subscription signal
- Reach (log-scaled view count)
- Age-range overlap
- Category match bonus



## Troubleshooting

- If bot spends too fast: lower bid factors and exposure cap.
- If bot stalls (`L100 Spent = 0`): lower ROI threshold or enable recovery spending.
- If score drops while wins are high: tighten ROI gate and reduce noisy affinity matches.
