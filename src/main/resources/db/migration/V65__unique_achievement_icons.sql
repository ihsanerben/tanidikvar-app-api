-- Keep existing awards and showcase selections; only catalog artwork changes.
UPDATE achievement_definitions AS d SET icon = icons.icon
FROM (VALUES
 ('FIRST_QUESTION', '❔'),
 ('QUESTIONS_10', '🧠'),
 ('FIRST_ANSWER', '💬'),
 ('ANSWERS_10', '🧭'),
 ('ANSWERS_50', '🌟'),
 ('FIRST_HELPFUL', '💛'),
 ('HELP_10', '🤝'),
 ('HELP_100', '🪄'),
 ('HELP_500', '🏆'),
 ('FIRST_EVALUATION', '🏫'),
 ('FIRST_POLL', '📣'),
 ('VOTES_10', '🗳️'),
 ('FIRST_EXPERIENCE', '✍️'),
 ('EXPERIENCES_5', '📖'),
 ('FIRST_METRIC', '📏'),
 ('METRICS_10', '📊'),
 ('TENURE_1', '🌱'),
 ('TENURE_3', '🌳'),
 ('PREFERENCE_GUIDE', '🧩'),
 ('NEW_STUDENT_GUIDE', '🎒'),
 ('FINAL_GUIDE', '📚'),
 ('ERASMUS_GUIDE', '✈️'),
 ('INTERNSHIP_GUIDE', '💼')
) AS icons(achievement_key, icon)
WHERE d.achievement_key = icons.achievement_key;
