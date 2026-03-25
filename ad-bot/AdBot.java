import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;


public class AdBot {

	private static final String DEFAULT_CATEGORY = "Sports";
	private static String botCategory = DEFAULT_CATEGORY;
	private static final double MIN_SPEND_RATIO = 0.10;
	private static final double MIN_MULTIPLIER = 0.65;
	private static final double MAX_MULTIPLIER = 1.80;


	private static final double ENGAGEMENT_WEIGHT = 0.38;
	private static final double INTEREST_WEIGHT = 0.34;
	private static final double SUBSCRIPTION_WEIGHT = 0.14;
	private static final double REACH_WEIGHT = 0.08;
	private static final double AGE_WEIGHT = 0.06;
	private static final double CATEGORY_MATCH_BONUS = 0.12;


	private static final double ENGAGEMENT_RATIO_CAP = 0.02;
	private static final double MAX_VIEWCOUNT = 100_000_000.0;
	private static final double TARGET_AGE_LOW = 25;
	private static final double TARGET_AGE_HIGH = 44;


	private static final double SPEND_PRESSURE_MULTIPLIER = 0.25;
	private static final double SIGNAL_MIN = 0.0;
	private static final double SIGNAL_MAX = 1.5;
	private static final double SIGNAL_LOW_THRESHOLD = 0.14;
	private static final double SIGNAL_MID_THRESHOLD = 0.35;
	private static final double SIGNAL_HIGH_THRESHOLD = 0.70;
	private static final double STRATEGY_BLEND_OLD = 0.60;
	private static final double STRATEGY_BLEND_NEW = 0.40;
	private static final double EFFICIENCY_TARGET_RATIO = 0.92;
	private static final int PLANNED_TOTAL_ROUNDS = 250_000;
	private static final int ROUND_FLOOR = 5_000;

	private static final double[] ARM_MULTIPLIERS = {0.80, 0.95, 1.05, 1.20, 1.35};
	private static final double EPSILON_START = 0.22;
	private static final double EPSILON_MIN = 0.03;
	private static final double EPSILON_DECAY = 0.9992;


	private static final int COMPETITION_LOW = 0;
	private static final int COMPETITION_MEDIUM = 1;
	private static final int COMPETITION_HIGH = 2;
	private static final double SHADE_AGGRESSIVE = 0.70;
	private static final double SHADE_BALANCED = 0.82;
	private static final double SHADE_CONSERVATIVE = 0.95;
	private static final int COMPETITION_WINDOW = 100;

	private static final String[] EMPTY_INTERESTS = new String[0];

	public static void main(String[] args) throws IOException {
		validateArguments(args);
		if (args.length == 2) {
			botCategory = normalizeCategory(args[1]);
		}
		long initialBudget = Long.parseLong(args[0]);
		if (initialBudget <= 0) {
			throw new IllegalArgumentException("Initial budget must be positive");
		}

		runBiddingLoop(initialBudget);
	}

	private static void validateArguments(String[] args) {
		if (args.length < 1 || args.length > 2) {
			throw new IllegalArgumentException("Expected arguments: <initial ebucks budget> [category]");
		}
	}

	private static String normalizeCategory(String rawCategory) {
		if (rawCategory == null) {
			return DEFAULT_CATEGORY;
		}
		String trimmed = rawCategory.trim();
		return trimmed.isEmpty() ? DEFAULT_CATEGORY : trimmed;
	}

	private static void runBiddingLoop(long initialBudget) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		PrintWriter writer = new PrintWriter(System.out, true);

		TrackingState state = new TrackingState(initialBudget);
		ImpressionData impression = new ImpressionData();

		writer.println(botCategory);

		String line;
		while ((line = reader.readLine()) != null) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty()) {
				continue;
			}

			if (isPlatformResponse(trimmedLine)) {
				handlePlatformResponse(trimmedLine, state);
			} else {
				processImpression(trimmedLine, impression, state, writer);
			}
		}
	}


	private static void processImpression(String line, ImpressionData impression, TrackingState state, PrintWriter writer) {
		if (state.remainingBudget <= 0) {
			writer.println("0 0");
			recordPendingBid(state, 0, 0, 0.0);
			return;
		}

		parseImpressionData(line, impression);
		double estimatedValue = estimateImpressionValue(impression);
		int roundsRemaining = estimateRoundsRemaining(state);
		
		int[] finalBid = computeFinalBid(estimatedValue, state, roundsRemaining);
		writer.println(finalBid[0] + " " + finalBid[1]);
		
		recordPendingBid(state, finalBid[0], finalBid[1], estimatedValue);
	}


	private static void recordPendingBid(TrackingState state, int startBid, int maxBid, double value) {
		state.pendingValue = value;
		state.hasPendingValue = true;
		state.pendingStartBid = startBid;
		state.pendingMaxBid = maxBid;
		state.totalRounds++;
	}


	private static double estimateImpressionValue(ImpressionData imp) {
		double engagementScore = calculateEngagementScore(imp.viewCount, imp.commentCount);
		double interestScore = calculateInterestScore(imp.videoCategory, imp.interests);
		double subscriptionScore = imp.subscribed ? 1.0 : 0.0;
		double reachScore = calculateReachScore(imp.viewCount);
		double ageScore = calculateAgeScore(imp.age);
		double categoryBonus = isCategoryMatch(imp.videoCategory) ? 1.0 : 0.0;

		double totalScore = 0.0;
		totalScore += ENGAGEMENT_WEIGHT * engagementScore;
		totalScore += INTEREST_WEIGHT * interestScore;
		totalScore += SUBSCRIPTION_WEIGHT * subscriptionScore;
		totalScore += REACH_WEIGHT * reachScore;
		totalScore += AGE_WEIGHT * ageScore;
		totalScore += CATEGORY_MATCH_BONUS * categoryBonus;

		return clamp(totalScore, 0.0, 1.0);
	}

	private static double calculateEngagementScore(long viewCount, long commentCount) {
		if (viewCount <= 0 || commentCount <= 0) {
			return 0.0;
		}
		double ratio = (double) commentCount / (double) viewCount;
		return ratio / (ratio + ENGAGEMENT_RATIO_CAP);
	}

	private static double calculateInterestScore(String videoCategory, String[] interests) {
		return calculateCategoryAffinity(videoCategory, interests);
	}

	private static double calculateReachScore(long viewCount) {
		if (viewCount <= 0) {
			return 0.0;
		}
		double capped = Math.min(MAX_VIEWCOUNT, (double) viewCount);
		return Math.log1p(capped) / Math.log1p(MAX_VIEWCOUNT);
	}

	private static double calculateAgeScore(String age) {
		return ageRangeScore(age);
	}

	private static boolean isCategoryMatch(String videoCategory) {
		return videoCategory != null && botCategory != null
				&& videoCategory.trim().equalsIgnoreCase(botCategory.trim());
	}

	private static double calculateCategoryAffinity(String videoCategory, String[] interests) {
		if (videoCategory == null || videoCategory.trim().isEmpty()) {
			return 0.0;
		}

		if (isCategoryMatch(videoCategory)) {
			return 1.0;
		}

		if (interests == null || interests.length == 0) {
			return 0.0;
		}

		for (String interest : interests) {
			if (interest != null && videoCategory.trim().equalsIgnoreCase(interest.trim())) {
				return 0.8;
			}
		}

		return 0.0;
	}


	private static int[] computeFinalBid(double estimatedValue, TrackingState state, int roundsRemaining) {
		int armIndex = chooseArm(state);
        state.lastChosenArm = armIndex;
		double effectiveMultiplier = state.bidMultiplier * ARM_MULTIPLIERS[armIndex];
		
		int[] baseBid = computeBaseBid(estimatedValue, state, roundsRemaining, effectiveMultiplier);
		if (baseBid[1] <= 0) {
			return new int[]{0, 0};
		}
		

		double budgetHealth = getBudgetHealth(state);
		double winRate = state.recentBids > 0 ? state.recentWins / (double) state.recentBids : 0.5;
		double shadeRatio = calculateBidShade(estimatedValue, state.competitionLevel, budgetHealth, winRate);
		boolean windowStalled = state.summaries > 0 && state.last100Spent == 0;
		if (windowStalled) {
			double recoveryShade = Math.max(shadeRatio, 0.90);
			return applyShadedBid(baseBid, recoveryShade);
		}
		
		if (shouldBid(estimatedValue, shadeRatio, state.competitionLevel, budgetHealth)) {
			return applyShadedBid(baseBid, shadeRatio);
		}
		return new int[]{0, 0};
	}


	private static int[] computeBaseBid(double value, TrackingState state, int roundsRemaining, double multiplier) {
		if (state.remainingBudget <= 0) {
			return new int[]{0, 0};
		}

		long paceBudget = Math.max(1L, state.remainingBudget / Math.max(1, roundsRemaining));
		double spentRatio = computeSpentRatio(state);
		double spendPressure = computeSpendPressure(spentRatio);
		double signal = clamp(value * multiplier * spendPressure, SIGNAL_MIN, SIGNAL_MAX);

		if (signal < SIGNAL_LOW_THRESHOLD && spentRatio >= MIN_SPEND_RATIO) {
			return new int[]{0, 0};
		}


		double startFactor = computeStartFactor(signal);
		double maxFactor = computeMaxFactor(signal);

		long startBid = Math.round(paceBudget * startFactor);
		long maxBid = Math.round(paceBudget * maxFactor);

		long exposureCap = computeExposureCap(state, signal);
		maxBid = Math.min(maxBid, exposureCap);


		if (spentRatio < MIN_SPEND_RATIO) {
			long floorBid = Math.max(1L, Math.round(state.remainingBudget * 0.002));
			startBid = Math.max(startBid, floorBid);
			maxBid = Math.max(maxBid, startBid);
		}


		startBid = Math.max(1L, Math.min(startBid, state.remainingBudget));
		maxBid = Math.max(startBid, Math.min(maxBid, state.remainingBudget));

		return new int[]{toIntClamped(startBid), toIntClamped(maxBid)};
	}

	private static double computeSpentRatio(TrackingState state) {
		return state.initialBudget > 0 ? state.totalSpent / (double) state.initialBudget : 1.0;
	}

	private static double computeSpendPressure(double spentRatio) {
		double deficit = Math.max(0.0, MIN_SPEND_RATIO - spentRatio);
		return 1.0 + (deficit * SPEND_PRESSURE_MULTIPLIER);
	}

	private static double computeStartFactor(double signal) {
		double factor = 0.14 + (0.62 * signal);
		if (signal > SIGNAL_HIGH_THRESHOLD) {
			factor += 0.16;
		}
		return factor;
	}

	private static double computeMaxFactor(double signal) {
		double factor = 0.32 + (1.00 * signal);
		if (signal > SIGNAL_HIGH_THRESHOLD) {
			factor += 0.24;
		} else if (signal < SIGNAL_MID_THRESHOLD) {
			factor *= 0.90;
		}
		return factor;
	}

	private static long computeExposureCap(TrackingState state, double signal) {
		double cappedSignal = clamp(signal, 0.0, 1.0);
		return Math.max(1L, Math.round(state.remainingBudget * (0.0020 + (0.0180 * cappedSignal))));
	}


	private static boolean isPlatformResponse(String line) {
		if (line.isEmpty()) {
			return false;
		}

		char code = line.charAt(0);
		return (code == 'W' || code == 'L' || code == 'S')
				&& (line.length() == 1 || Character.isWhitespace(line.charAt(1)));
	}


	public static void handlePlatformResponse(String responseLine, TrackingState state) {
		enforceNotNull(state, "Tracking state cannot be null");
		enforceNotNull(responseLine, "Response line cannot be null");

		String[] parts = responseLine.split("\\s+");
		String code = parts[0];

		if ("W".equals(code)) {
			if (parts.length != 2) {
				throw new IllegalArgumentException("Malformed win response: " + responseLine);
			}

			long spent = parseLongOrZero(parts[1]);
			if (spent < 0) {
				throw new IllegalArgumentException("Spent cannot be negative: " + responseLine);
			}

			state.remainingBudget = Math.max(0L, state.remainingBudget - spent);
			state.totalSpent += spent;
			state.wins++;

			if (state.hasPendingValue) {
				state.totalValueWon += state.pendingValue;
                state.armPullCounts[state.lastChosenArm]++;
                state.armTotalValues[state.lastChosenArm] += state.pendingValue;
			}

			updateCompetitionMetrics(state, true, spent, state.pendingStartBid);
			state.hasPendingValue = false;
			return;
		}

		if ("L".equals(code)) {
			if (parts.length != 1) {
				throw new IllegalArgumentException("Malformed loss response: " + responseLine);
			}

			state.losses++;
            state.armPullCounts[state.lastChosenArm]++;
			updateCompetitionMetrics(state, false, 0L, state.pendingStartBid);
			state.hasPendingValue = false;
			return;
		}

		if ("S".equals(code)) {
			if (parts.length != 3) {
				throw new IllegalArgumentException("Malformed summary response: " + responseLine);
			}

			long pointsLast100 = parseLongOrZero(parts[1]);
			long ebucksLast100 = parseLongOrZero(parts[2]);
			if (pointsLast100 < 0 || ebucksLast100 < 0) {
				throw new IllegalArgumentException("Summary values cannot be negative: " + responseLine);
			}

			state.last100Points = pointsLast100;
			state.last100Spent = ebucksLast100;
			state.totalPoints += pointsLast100;
			state.summaries++;


			double currentEfficiency = computeOverallEfficiency(state);
			double recentEfficiency = computeRecentEfficiency(pointsLast100, ebucksLast100, currentEfficiency);

			updateBestEfficiency(state, recentEfficiency);

			double targetEfficiency = Math.max(1e-9, state.bestObservedEfficiency * EFFICIENCY_TARGET_RATIO);
			double newMultiplier = adjustStrategy(pointsLast100, ebucksLast100, currentEfficiency, targetEfficiency);

			blendMultiplier(state, newMultiplier);
			return;
		}

		throw new IllegalArgumentException("Unknown platform response: " + responseLine);
	}

	private static void enforceNotNull(Object obj, String message) {
		if (obj == null) {
			throw new IllegalArgumentException(message);
		}
	}

	private static double computeOverallEfficiency(TrackingState state) {
		return state.totalSpent > 0 ? state.totalValueWon / (double) state.totalSpent : 0.0;
	}

	private static double computeRecentEfficiency(long points, long ebucks, double currentEfficiency) {
		return ebucks > 0 ? points / (double) ebucks : currentEfficiency;
	}

	private static void updateBestEfficiency(TrackingState state, double recentEfficiency) {
		if (recentEfficiency > state.bestObservedEfficiency) {
			state.bestObservedEfficiency = recentEfficiency;
		}
	}

	private static void blendMultiplier(TrackingState state, double newMultiplier) {
		double blended = (STRATEGY_BLEND_OLD * state.bidMultiplier) + (STRATEGY_BLEND_NEW * newMultiplier);
		state.bidMultiplier = clamp(blended, MIN_MULTIPLIER, MAX_MULTIPLIER);
	}

	public static void parseImpressionData(String line, ImpressionData out) {
		if (line == null || out == null) {
			throw new IllegalArgumentException("Line and output object must be non-null");
		}

		out.videoCategory = "";
		out.viewCount = 0L;
		out.commentCount = 0L;
		out.subscribed = false;
		out.age = "";
		out.gender = "";
		out.interests = EMPTY_INTERESTS;

		char[] chars = line.toCharArray();
		int len = chars.length;
		int pos = 0;

		while (pos < len) {
			int keyStart = pos;
			int eqIdx = -1;
			
			while (pos < len && chars[pos] != '=') {
				pos++;
			}
			eqIdx = pos;
			if (eqIdx >= len) break;

			pos++;
			int valStart = pos;
			int valEnd = pos;

			while (pos < len && chars[pos] != ',') {
				valEnd = pos;
				pos++;
			}

			parseAndAssignField(chars, keyStart, eqIdx, valStart, valEnd, out);
			if (pos < len && chars[pos] == ',') {
				pos++;
			}
		}
	}

	private static void parseAndAssignField(char[] chars, int keyStart, int eqIdx, int valStart, int valEnd, ImpressionData out) {
		int keyLen = eqIdx - keyStart;

		if (keyLen == 15 && match(chars, keyStart, "video.viewCount")) {
			out.viewCount = parseLongFast(chars, valStart, valEnd + 1);
		} else if (keyLen == 17 && match(chars, keyStart, "video.commentCount")) {
			out.commentCount = parseLongFast(chars, valStart, valEnd + 1);
		} else if (keyLen == 14 && match(chars, keyStart, "video.category")) {
			out.videoCategory = new String(chars, valStart, valEnd - valStart + 1).trim();
		} else if (keyLen == 17 && match(chars, keyStart, "viewer.subscribed")) {
			char first = chars[valStart];
			out.subscribed = (first == 'Y' || first == 'y' || first == 't' || first == 'T');
		} else if (keyLen == 10 && match(chars, keyStart, "viewer.age")) {
			out.age = new String(chars, valStart, valEnd - valStart + 1).trim();
		} else if (keyLen == 13 && match(chars, keyStart, "viewer.gender")) {
			out.gender = new String(chars, valStart, valEnd - valStart + 1).trim();
		} else if (keyLen == 16 && match(chars, keyStart, "viewer.interests")) {
			out.interests = splitInterestsFast(chars, valStart, valEnd + 1);
		}
	}

	private static boolean match(char[] chars, int start, String expected) {
		int len = expected.length();
		for (int i = 0; i < len; i++) {
			if (chars[start + i] != expected.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	private static long parseLongFast(char[] chars, int start, int end) {
		long result = 0;
		for (int i = start; i < end; i++) {
			char c = chars[i];
			if (c >= '0' && c <= '9') {
				result = result * 10 + (c - '0');
			}
		}
		return result;
	}

	private static String[] splitInterestsFast(char[] chars, int start, int end) {
		if (start >= end) {
			return EMPTY_INTERESTS;
		}

		int count = 1;
		for (int i = start; i < end; i++) {
			if (chars[i] == ';') {
				count++;
			}
		}

		String[] parts = new String[count];
		int idx = 0;
		int itemStart = start;

		for (int i = start; i <= end; i++) {
			if (i == end || chars[i] == ';') {
				int itemEnd = i;
				while (itemStart < itemEnd && chars[itemStart] == ' ') itemStart++;
				while (itemEnd > itemStart && chars[itemEnd - 1] == ' ') itemEnd--;
				parts[idx++] = new String(chars, itemStart, itemEnd - itemStart);
				itemStart = i + 1;
			}
		}
		return parts;
	}

	public static double calculateBidValue(
			String videoCategory,
			long viewCount,
			long commentCount,
			boolean subscribed,
			String age,
			String gender,
			String[] interests,
			String myCategory) {
		double score = 0.0;


		double engagementScore = 0.0;
		if (viewCount > 0 && commentCount > 0) {
			double ratio = (double) commentCount / (double) viewCount;
			engagementScore = ratio / (ratio + 0.02);
		}

		double interestCategoryScore = interestCategoryAffinity(videoCategory, interests);

		double subscriptionScore = subscribed ? 1.0 : 0.0;


		double viewScaleScore = 0.0;
		if (viewCount > 0) {
			double capped = Math.min(100_000_000.0, (double) viewCount);
			viewScaleScore = Math.log1p(capped) / Math.log1p(100_000_000.0);
		}

		double ageScore = ageRangeScore(age);

		score += 0.38 * engagementScore;
		score += 0.28 * interestCategoryScore;
		score += 0.14 * subscriptionScore;
		score += 0.14 * viewScaleScore;
		score += 0.06 * ageScore;

		if (myCategory != null && videoCategory != null
				&& videoCategory.trim().equalsIgnoreCase(myCategory.trim())) {
			score += 0.05;
		}

		if (gender != null && gender.isEmpty()) {
			score += 0.0;
		}

		return clamp(score, 0.0, 1.0);
	}

	private static double interestCategoryAffinity(String videoCategory, String[] interests) {
		if (interests == null || interests.length == 0) {
			return 0.0;
		}

		String category = videoCategory == null ? "" : videoCategory.trim().toLowerCase();
		if (category.isEmpty()) {
			return 0.0;
		}

		int considered = 0;
		double total = 0.0;
		for (String interest : interests) {
			if (interest == null) {
				continue;
			}
			String token = interest.trim().toLowerCase();
			if (token.isEmpty()) {
				continue;
			}

			considered++;
			if (token.equals(category) || token.contains(category) || category.contains(token)) {
				total += 1.0;
			} else if (isFinanceLike(token) && isFinanceLike(category)) {
				total += 0.85;
			} else if (isEntertainmentLike(token) && isEntertainmentLike(category)) {
				total += 0.25;
			}
		}

		if (considered == 0) {
			return 0.0;
		}

		return clamp(total / considered, 0.0, 1.0);
	}

	private static boolean isFinanceLike(String value) {
		return value.contains("finance")
				|| value.contains("invest")
				|| value.contains("business")
				|| value.contains("econom")
				|| value.contains("money")
				|| value.contains("market")
				|| value.contains("stock");
	}

	private static boolean isEntertainmentLike(String value) {
		return value.contains("music")
				|| value.contains("sports")
				|| value.contains("gaming")
				|| value.contains("movies")
				|| value.contains("film")
				|| value.contains("comedy")
				|| value.contains("lifestyle");
	}

	public static int[] calculateBid(double value, long remainingBudget, long initialBudget, int roundsRemaining) {
		long totalSpent = Math.max(0L, initialBudget - remainingBudget);
		return calculateBid(value, remainingBudget, initialBudget, roundsRemaining, 1.0, totalSpent, 0, 0);
	}

	public static int[] calculateBid(
			double value,
			long remainingBudget,
			long initialBudget,
			int roundsRemaining,
			double multiplier,
			long totalSpent) {
		return calculateBid(value, remainingBudget, initialBudget, roundsRemaining, multiplier, totalSpent, 0, 0);
	}

	public static int[] calculateBid(
			double value,
			long remainingBudget,
			long initialBudget,
			int roundsRemaining,
			double multiplier,
			long totalSpent,
			int wins,
			int losses) {
		if (remainingBudget <= 0) {
			return new int[] {0, 0};
		}

		int safeRounds = Math.max(1, roundsRemaining);
		long paceBudget = Math.max(1L, remainingBudget / safeRounds);

		double spentRatio = initialBudget > 0
				? totalSpent / (double) initialBudget
				: 1.0;

		double totalOutcomes = Math.max(1.0, (double) wins + (double) losses);
		double winRate = wins / totalOutcomes;
		double lossRate = losses / totalOutcomes;

		double historyAggression = 1.0;
		if (lossRate > 0.58) {
			historyAggression += Math.min(0.28, (lossRate - 0.58) * 0.80);
		} else if (winRate > 0.68) {
			historyAggression -= Math.min(0.18, (winRate - 0.68) * 0.70);
		}

		double spendPressure = 1.0 + Math.max(0.0, MIN_SPEND_RATIO - spentRatio) * 2.4;
		double signal = clamp(value * multiplier * spendPressure * historyAggression, 0.0, 1.5);

		if (signal < 0.12 && spentRatio >= MIN_SPEND_RATIO) {
			return new int[] {0, 0};
		}

		double startFactor = 0.20 + (0.90 * signal);
		double maxFactor = 0.50 + (1.40 * signal);

		if (signal > 0.70) {
			startFactor += 0.25;
			maxFactor += 0.45;
		} else if (signal < 0.35) {
			startFactor *= 0.60;
			maxFactor *= 0.80;
		}

		long startBid = Math.round(paceBudget * startFactor);
		long maxBid = Math.round(paceBudget * maxFactor);

		double cappedSignal = clamp(signal, 0.0, 1.0);
		long exposureCap = Math.max(1L, Math.round(remainingBudget * (0.01 + (0.09 * cappedSignal))));
		maxBid = Math.min(maxBid, exposureCap);

		if (spentRatio < MIN_SPEND_RATIO) {
			long floorBid = Math.max(1L, Math.round(remainingBudget * 0.002));
			startBid = Math.max(startBid, floorBid);
			maxBid = Math.max(maxBid, startBid);
		}

		startBid = Math.max(1L, startBid);
		maxBid = Math.max(startBid, maxBid);

		startBid = Math.min(startBid, remainingBudget);
		maxBid = Math.min(maxBid, remainingBudget);

		return new int[] {toIntClamped(startBid), toIntClamped(maxBid)};
	}

	public static double adjustStrategy(
			double pointsLast100,
			long ebucksLast100,
			double currentEfficiency,
			double targetEfficiency) {
		if (targetEfficiency <= 0.0) {
			return 1.0;
		}

		double recentEfficiency = currentEfficiency;
		if (ebucksLast100 > 0) {
			recentEfficiency = pointsLast100 / (double) ebucksLast100;
		}

		double blendedEfficiency = (0.75 * recentEfficiency) + (0.25 * currentEfficiency);
		double ratio = blendedEfficiency / targetEfficiency;

		double multiplier;
		if (ratio < 1.0) {
			double deficit = 1.0 - ratio;
			multiplier = 1.0 + Math.min(0.45, deficit * 0.90);
		} else {
			double surplus = ratio - 1.0;
			multiplier = 1.0 - Math.min(0.18, surplus * 0.25);
		}

		return clamp(multiplier, MIN_MULTIPLIER, MAX_MULTIPLIER);
	}

	private static void detectCompetitionLevel(TrackingState state) {
		state.roundsSinceCompetitionShift++;
		if (state.recentBids < 10) {
			return;
		}

		double winRate = state.recentWins / (double) state.recentBids;
		double priceInflation = state.recentWins > 0
				? state.avgWinPrice / Math.max(1.0, state.bestObservedEfficiency * 100)
				: 1.0;

		int newLevel = COMPETITION_MEDIUM;

		if (winRate > 0.65 && state.bidVariance < 0.4 && priceInflation < 1.2) {
			newLevel = COMPETITION_LOW;
		} else if (winRate < 0.35 || state.bidVariance > 0.7 || priceInflation > 1.6) {
			newLevel = COMPETITION_HIGH;
		}

		if (newLevel != state.competitionLevel) {
			state.competitionLevel = newLevel;
			state.roundsSinceCompetitionShift = 0;
		}
	}

	private static double calculateBidShade(double value, int competitionLevel, double budgetHealth, double winRate) {
		double baseShade = SHADE_BALANCED;

		if (competitionLevel == COMPETITION_LOW) {
			baseShade = SHADE_CONSERVATIVE;
		} else if (competitionLevel == COMPETITION_HIGH) {
			baseShade = SHADE_AGGRESSIVE;
		}

		double budgetAdjustment = 1.0 - (budgetHealth * 0.15);
		double shadeRatio = clamp(baseShade * budgetAdjustment, SHADE_AGGRESSIVE, SHADE_CONSERVATIVE);

		double winRateAdjustment = 1.0 - (Math.max(0.0, winRate - 0.40) * 0.30);
		return clamp(shadeRatio * winRateAdjustment, SHADE_AGGRESSIVE, SHADE_CONSERVATIVE);
	}

	private static boolean shouldBid(double value, double shadeRatio, int competitionLevel, double budgetHealth) {
		double shadedValue = value * shadeRatio;
		double roiThreshold = competitionLevel == COMPETITION_LOW ? 0.46 : 0.26;

		if (budgetHealth < 0.15) {
			roiThreshold *= 0.60;
		}
		if (competitionLevel == COMPETITION_HIGH) {
			roiThreshold *= 0.78;
		}
		if (budgetHealth > 0.70) {
			roiThreshold *= 1.08;
		}

		return shadedValue >= roiThreshold;
	}

	private static int[] applyShadedBid(int[] baseBid, double shadeRatio) {
		long startBid = (long) (baseBid[0] * shadeRatio);
		long maxBid = (long) (baseBid[1] * shadeRatio);
		return new int[] {toIntClamped(Math.max(1L, startBid)), toIntClamped(Math.max(2L, maxBid))};
	}


	private static int chooseArm(TrackingState state) {
		double epsilon = Math.max(EPSILON_MIN, EPSILON_START * Math.pow(EPSILON_DECAY, state.totalRounds));
		
		if (Math.random() < epsilon) {
			return (int) (Math.random() * ARM_MULTIPLIERS.length);
		}
		
		if (state.armPullCounts == null || state.armPullCounts.length == 0) {
			return 0;
		}
		
		int bestArm = 0;
		double bestValue = -Double.MAX_VALUE;
		for (int i = 0; i < state.armPullCounts.length; i++) {
			long pulls = state.armPullCounts[i];
			if (pulls == 0) {
				return i;
			}
			double armValue = state.armTotalValues[i] / (double) pulls;
			if (armValue > bestValue) {
				bestValue = armValue;
				bestArm = i;
			}
		}
		return bestArm;
	}

	private static void updateCompetitionMetrics(TrackingState state, boolean won, long wonPrice, long bidAmount) {
		state.recentBids = (state.recentBids % COMPETITION_WINDOW) + 1;
		
		if (won) {
			state.recentWins = (state.recentWins % COMPETITION_WINDOW) + 1;
			state.lastWinPrice = wonPrice;
			state.totalWonAt += wonPrice;
			double newAvg = state.totalWonAt / (double) state.recentWins;
			state.avgWinPrice = 0.7 * state.avgWinPrice + 0.3 * newAvg;
		} else {
			state.nearMisses++;
		}

		double bidVarianceUpdate = 0.0;
		if (state.lastWinPrice > 0) {
			bidVarianceUpdate = Math.abs(bidAmount - state.lastWinPrice) / (double) state.lastWinPrice;
		}
		state.bidVariance = 0.85 * state.bidVariance + 0.15 * bidVarianceUpdate;
		
		detectCompetitionLevel(state);
	}

	private static double getBudgetHealth(TrackingState state) {
		return clamp(state.remainingBudget / (double) Math.max(1L, state.initialBudget), 0.0, 1.0);
	}

	private static int estimateRoundsRemaining(TrackingState state) {
		int remaining = PLANNED_TOTAL_ROUNDS - state.totalRounds;
		return Math.max(ROUND_FLOOR, remaining);
	}

	private static double ageRangeScore(String age) {
		if (age == null || age.trim().isEmpty()) {
			return 0.0;
		}

		String value = age.trim();
		if (!value.contains("-")) {
			return 0.0;
		}

		String[] parts = value.split("-");
		if (parts.length != 2) {
			return 0.0;
		}

		try {
			int low = Integer.parseInt(parts[0].trim());
			int high = Integer.parseInt(parts[1].trim());
			if (low > high) {
				return 0.0;
			}

			int overlapLow = Math.max(low, 25);
			int overlapHigh = Math.min(high, 44);
			if (overlapLow > overlapHigh) {
				return 0.0;
			}

			int overlapSpan = overlapHigh - overlapLow + 1;
			int totalSpan = high - low + 1;
			return (double) overlapSpan / (double) totalSpan;
		} catch (NumberFormatException ex) {
			return 0.0;
		}
	}

	private static long parseLongOrZero(String value) {
		if (value == null || value.isEmpty()) {
			return 0L;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ex) {
			return 0L;
		}
	}

	private static double clamp(double v, double min, double max) {
		if (v < min) {
			return min;
		}
		if (v > max) {
			return max;
		}
		return v;
	}

	private static int toIntClamped(long value) {
		if (value <= 0) {
			return 0;
		}
		if (value > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) value;
	}

	public static final class ImpressionData {
		public String videoCategory;
		public long viewCount;
		public long commentCount;
		public boolean subscribed;
		public String age;
		public String gender;
		public String[] interests;
	}

	public static final class TrackingState {
		public final long initialBudget;
		public long remainingBudget;
		public long totalSpent;
		public long totalPoints;
		public long last100Points;
		public long last100Spent;
		public double totalValueWon;
        public int lastChosenArm;
		public int wins;
		public int losses;
		public int summaries;
		public int totalRounds;
		public double bidMultiplier;
		public double bestObservedEfficiency;
		public double pendingValue;
		public boolean hasPendingValue;
		public int competitionLevel;
		public int recentWins;
		public int recentBids;
		public long totalWonAt;
		public int nearMisses;
		public double bidVariance;
		public long lastWinPrice;
		public double avgWinPrice;
		public double prevRoundEfficiency;
		public int roundsSinceCompetitionShift;
		public int pendingStartBid;
		public int pendingMaxBid;
		public long[] armPullCounts;
		public double[] armTotalValues;

		public TrackingState(long initialBudget) {
			this.initialBudget = initialBudget;
			this.remainingBudget = initialBudget;
			this.bidMultiplier = 1.0;
			this.bestObservedEfficiency = 0.0;
			this.pendingValue = 0.0;
			this.hasPendingValue = false;
			this.competitionLevel = COMPETITION_MEDIUM;
			this.recentWins = 0;
			this.recentBids = 0;
			this.totalWonAt = 0L;
			this.nearMisses = 0;
			this.bidVariance = 0.5;
			this.lastWinPrice = 0L;
			this.avgWinPrice = 0.0;
			this.prevRoundEfficiency = 0.0;
			this.roundsSinceCompetitionShift = 0;
			this.pendingStartBid = 0;
			this.pendingMaxBid = 0;
            this.lastChosenArm = 0;
			this.armPullCounts = new long[ARM_MULTIPLIERS.length];
			this.armTotalValues = new double[ARM_MULTIPLIERS.length];
		}
	}
}