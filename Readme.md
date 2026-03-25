# AdBot — Playtech Summer Internship 2026

A Java-based bidding bot for the Playtech Summer 2026 internship assignment.
The bot participates in a simulated video ad auction, competing against other 
bots to maximize points per ebuck spent.

## Strategy

The bot advertises in the **Finance** category and evaluates each impression 
using a weighted scoring model:

- Viewer engagement (comment/view ratio)
- Interest match with Finance category
- Subscription status
- Audience reach
- Age bracket (25–44 is the prime Finance demographic)

Bidding aggressiveness is controlled by an **epsilon-greedy multi-armed bandit**
that learns which bid multiplier performs best over time. The bot also detects
competition level (low/medium/high) based on win rate and adjusts its strategy
every 100 rounds using efficiency feedback from the platform summaries.

## Notes

My university studies focus on C#, so this was my first hands-on Java project.
I used GitHub Copilot to help with Java syntax and boilerplate, while designing
the overall bidding strategy myself.
