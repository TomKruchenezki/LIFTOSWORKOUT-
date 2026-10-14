# FINAL_REQUIREMENTS_AUDIT - מטריצת דרישות לקבלה סופית

> נוצר בביקורת קבלה סופית. Commit: `e9aa61f773ea9c476d1f121d4221da70ebf6a2c3` · ריצת CI: `38565145090` · תוצאת CI: **success (כל השלבים, כולל Test gate)** · גרסה `0.2.1 (versionCode 3)`.
> הקוד הוא מקור האמת למימוש; פרומפטים 1 ו-2 הם מקור האמת לדרישות. כל סטטוס PASS נתמך בבדיקה אוטומטית שרצה ועברה ב-CI, לא בעצם קיום הקוד.

## 1. שיטה, מקרא ומגבלות

**מקורות.** שני הפרומפטים נקראו במלואם מהתמלול: פרומפט 1 (14 סעיפים, 8,343 תווים) ופרומפט 2 (23 סעיפים, 18,516 תווים, הגרסה המתוקנת ללא תזונה). פרומפט ה-master הארוך נחתך בתמלול ב-20,000 תווים (באמצע סעיף 'בדיקות תוכנה') - **לא נבדק מעבר לחפיפה עם פרומפטים 1 ו-2** (OPEN-MASTER).

**סטטוסים.** `PASS` - מומש ונבדק בבדיקה אוטומטית שעברה. `PARTIAL` - מומש בחלקו או שחלק מההתנהגות לא נבדק. `FAIL` - נבדק ונכשל. `MISSING` - לא קיים. `BLOCKED` - אי אפשר לבדוק בסביבה הזו (דרוש מכשיר/שירות). `FUTURE` - דרישה עתידית מסומנת מראש. `NOT VERIFIED` - ציון מפורש בעמודת הפער שההתנהגות הזו לא הורצה בפועל.

**מפתח אימות.** `D` בדיקות domain (JUnit על JVM) · `R` בדיקות app עם Robolectric (Room, שירותים, התראות, Intents) · `U` בדיקות Compose UI תחת Robolectric (אמולציה על JVM, לא מכשיר) · `B` בנייה ב-CI · `C` קריאת קוד בלבד · `X` דורש מכשיר אמיתי ולא הורץ.

**מה לא נבדק אף פעם (בכל השורות):** שום דבר לא הורץ על מכשיר פיזי (Samsung Galaxy S26 Ultra) או אמולטור. בדיקות ה-UI הן Robolectric על JVM, ללא רינדור חזותי שנבדק על ידי אדם; לא נבדקו צילומי מסך. ארטיפקטים של GitHub אינם ניתנים להורדה מסביבת העבודה (מדיניות רשת) - הוכחות ה-APK מגיעות מלוג ה-CI ומה-API של GitHub.

## 2. סיכום מספרי

| פרומפט | דרישות | PASS | PARTIAL | FAIL | MISSING | BLOCKED | FUTURE |
|---|---|---|---|---|---|---|---|
| פרומפט 1 | 107 | 97 | 6 | 0 | 0 | 3 | 1 |
| פרומפט 2 | 150 | 134 | 7 | 0 | 0 | 4 | 5 |
| **סה"כ** | 257 | 231 | 13 | 0 | 0 | 7 | 6 |

הספירה היא של שורות אטומיות במטריצות שלהלן (סעיפי הפרומפט מפוצלים להתנהגויות בודדות). שורת PASS כוללת רק את מה שנבדק; החלק שלא נבדק מופיע בשורה נפרדת כ-`BLOCKED` או `PARTIAL`.

## 3. מטריצה - פרומפט 1 (חלק 1)

| ID | § | דרישה | סטטוס | קוד | אימות | תוצאה | פער / פעולה |
|---|---|---|---|---|---|---|---|
| P1-1.01 | 1 | Kotlin + Jetpack Compose + Material 3 + MVVM | PASS | app/ui/**, *ViewModel.kt | B: assembleDebug+assembleRelease; U: AppSmokeTest | ✔ |  |
| P1-1.02 | 1 | Room/SQLite, Coroutines/StateFlow, Repository pattern | PASS | app/data/db, app/data/repo | R: WorkoutRepositoryTest(34), DurabilityTest(5), SeederAndPlanTest(12) | ✔ |  |
| P1-1.03 | 1 | Navigation Compose + DataStore | PASS | ui/nav/LiftOsRoot.kt, data/prefs/DevicePrefs.kt | U: AppSmokeTest.bottom_tabs_open_their_screens, AppFlowsTest; R: SeederAndPlanTest (device flags) | ✔ |  |
| P1-1.04 | 1 | Hilt - רק אם יש הצדקה | PASS | di/AppContainer.kt | C: החלטה מתועדת ב-CLAUDE.md (אפליקציה קטנה, DI ידני, בנייה מהירה) | — | הדרישה מותנית; לא נעשה שימוש ב-Hilt במכוון |
| P1-1.05 | 1 | הפרדה בין UI, Domain, Data, Database ו-Services | PASS | :domain (Kotlin טהור), app/data, app/service, app/ui | B: מודול domain נבנה ונבדק בלי Android (-Pliftos.domainOnly); D: 287 בדיקות | ✔ |  |
| P1-1.06 | 1 | מערכת מודולרית שניתנת להרחבה | PASS | AiProvider.kt, FlootImporter.kt, BackupCodec.kt | D: AiProviderTest, FlootImporterTest, BackupV2Test | ✔ |  |
| P1-1.07 | 1 | בדיקת תאימות וגרסאות ספריות לפני שימוש | PASS | gradle/libs.versions.toml | B: בנייה ירוקה עם גרסאות נעוצות; CLAUDE.md מציין 'verified Oct 2026' | ✔ |  |
| P1-2.01 | 2 | ממשק בעברית מלאה | PASS | מחרוזות Compose בעברית; labelHe ב-domain | U: ScreenSweepTest + AppSmokeTest מציגים טקסט עברי בכל המסכים המרכזיים | ✔ | לא נסרקו כל המחרוזות אוטומטית; שמות תרגילים/תוכניות באנגלית לפי הפרומפט |
| P1-2.02 | 2 | RTL | PASS | ui/theme/Theme.kt (LayoutDirection.Rtl) | U: ScreenSweepTest.the_whole_app_is_laid_out_right_to_left | ✔ | הצגה חזותית בפועל לא נבדקה (NOT VERIFIED visually) |
| P1-2.03 | 2 | מצב כהה כברירת מחדל | PASS | AppSettings.themeMode = DARK; Theme.kt | U: ThemeTest.the_default_theme_is_dark_charcoal_with_a_lime_accent (ThemeMode.DARK כברירת מחדל) | ✔ |  |
| P1-2.04 | 2 | רקע פחם + הדגשות ליים | PASS | Theme.kt (Charcoal 0E100F, Lime B6F23C) | U: ThemeTest.the_default_theme_is_dark_charcoal_with_a_lime_accent (בהירות הרקע, דומיננטיות ירוק בצבע ההדגשה) | ✔ | הצבעים נבדקו כמספרים, לא על מסך אמיתי |
| P1-2.05 | 2 | כרטיסיות מעוגלות | PASS | LiftCard (RoundedCornerShape 20dp), AppShapes | U: ThemeTest.cards_and_surfaces_use_rounded_corners | ✔ |  |
| P1-2.06 | 2 | אייקונים ברורים | PASS | Material Icons Extended + contentDescription | U: חיפוש לפי contentDescription ('סמן כבוצע','דלג על המנוחה','הגדרות','חזרה','ערוך אימון') | ✔ |  |
| P1-2.07 | 2 | אנימציות עדינות | PARTIAL | מעברי fade ב-LiftOsRoot, animateColorAsState בשורת סט (תוקן בביקורת) | C; U: הזרימות רצות עם האנימציות בלי קריסה | — | NOT VERIFIED: איכות האנימציה לא נראתה; מינימלית (G5, Low) |
| P1-2.08 | 2 | מסכים נקיים ומודרניים; תחושת מוצר מסחרי | PARTIAL | כל מסכי ui/* | U: רינדור מוצלח; לא נבדק חזותית | — | NOT VERIFIED: אין צילומי מסך ואין בדיקה על מכשיר |
| P1-2.09 | 2 | ניווט תחתון | PASS | LiftOsRoot.kt (5 לשוניות) | U: AppSmokeTest.bottom_tabs_open_their_screens | ✔ |  |
| P1-2.10 | 2 | כפתורים גדולים ונוחים בזמן אימון | PASS | BigButton 58dp; שורת סט 48dp; כפתור סימון 48dp | U: ScreenSweepTest.the_main_buttons_are_big_enough_for_a_thumb | ✔ |  |
| P1-2.11 | 2 | הקלדה מהירה במקלדת מספרית | PARTIAL | ui/components/NumberFields.kt (KeyboardType.Decimal/Number) | U: AppSmokeTest מקליד 60 ו-8; C: סוג המקלדת | ✔ / X | NOT VERIFIED: סוג המקלדת בפועל נבדק רק בקוד (Robolectric לא חושף keyboardType) |
| P1-2.12 | 2 | מצב בהיר (עתידי) | PASS | ThemeMode.LIGHT/SYSTEM בהגדרות | U: ThemeTest.the_light_theme_is_really_light; ScreenSweepTest.the_light_theme_renders_the_home_screen_and_a_workout | ✔ | קיים בפועל, לא רק עתידי |
| P1-2.13 | 2 | אנגלית (עתידי) | FUTURE | מחרוזות עברית hardcoded; AppSettings.language קיים | C | — | הכנה חלקית בלבד: חילוץ ל-strings.xml לא בוצע (CLAUDE.md) |
| P1-3.01 | 3 | האימון הבא | PASS | HomeViewModel + NextWorkoutResolver | D: NextWorkoutResolverTest(7); U: AppSmokeTest.home_shows_the_next_workout_with_a_start_button | ✔ |  |
| P1-3.02 | 3 | מספר אימונים שבוצעו השבוע והחודש | PASS | HomeViewModel.observeCountBetween | R: HomeDataTest.counts only finished workouts inside the date range; U: AppSmokeTest ('אימונים השבוע') | ✔ |  |
| P1-3.03 | 3 | המשקל האחרון שתועד | PASS | MeasurementRepository.observeLatestWeight | R: HomeDataTest.the latest weight skips...; U: ScreenSweepTest.home_shows_the_data_of_the_week | ✔ |  |
| P1-3.04 | 3 | אימון אחרון | PASS | WorkoutRepository.observeLastCompleted | R: HomeDataTest.the last workout is the newest finished one | ✔ |  |
| P1-3.05 | 3 | מטרת האימון הנוכחית | PASS | HomeScreen ('מטרה נוכחית' מתוך plan.goal) | D: DefaultPlanTableTest/LibraryAndPlanTest (goal='ירידה בשומן ושיפור מסת שריר'); C: הצגה בכרטיס | ✔ | מוצג רק כשלתוכנית הפעילה יש מטרה |
| P1-3.06 | 3 | גישה מהירה לתוכניות | PASS | HomeScreen (LazyRow של תוכניות) | U: ScreenSweepTest.the_plan_shortcut_on_home_opens_the_plan | ✔ |  |
| P1-3.07 | 3 | כפתור 'התחל אימון' | PASS | HomeScreen | U: AppSmokeTest.a_workout_can_be_started_and_a_set_logged... | ✔ |  |
| P1-3.08 | 3 | כפתור 'המשך אימון' כשיש אימון פעיל | PASS | HomeScreen + באנר ב-LiftOsRoot | U: ScreenSweepTest.a_running_workout_is_offered_for_continuing_on_home | ✔ |  |
| P1-3.09 | 3 | נתונים מהמסד האמיתי בלבד, בלי נתונים פיקטיביים | PASS | כל ה-ViewModels קוראים ממסד | D: DashboardTest.empty database gives empty cards; U: AppSmokeTest (מצב ריק 'עוד לא תועד משקל') | ✔ |  |
| P1-4.01 | 4 | יצירה וניהול של כמה תוכניות | PASS | PlanRepository, PlansScreen | R: SeederAndPlanTest.create a plan, add a workout and exercises, then edit them; U: AppFlowsTest.plan_screens_open_down_to_the_exercise_editor | ✔ |  |
| P1-4.02 | 4 | שינוי שם ותיאור | PASS | PlanDetailScreen ('ערוך שם, תיאור ויום') | R: SeederAndPlanTest (create/edit) | ✔ |  |
| P1-4.03 | 4 | הוספה, הסרה והחלפה של תרגילים | PASS | PlanWorkoutScreen | R: SeederAndPlanTest.exercises can be reordered, duplicated, swapped and removed | ✔ |  |
| P1-4.04 | 4 | שינוי סדר תרגילים בגרירה | PARTIAL | ui/components/ReorderSheet.kt (reorderable) | R: הסדר החדש נשמר (reordered) | ✔ / X | NOT VERIFIED: תנועת הגרירה עצמה אינה ניתנת לבדיקה ב-Robolectric |
| P1-4.05 | 4 | עריכת סטים, טווח חזרות, משקל יעד, RIR/RPE, מנוחה, הערות | PASS | PlanWorkoutScreen, PlanRepository | R: SeederAndPlanTest.invalid planned values are clamped to sane limits; U: עורך התרגיל נפתח | ✔ | הקלדת ערכים בטופס לא נבדקה ב-UI |
| P1-4.06 | 4 | שכפול תוכנית | PASS | PlansScreen ('שכפל תוכנית') | R: SeederAndPlanTest.duplicate creates an independent deep copy | ✔ |  |
| P1-4.07 | 4 | מחיקת תוכנית | PASS | PlansScreen + אישור | R: SeederAndPlanTest.deleting a plan keeps workout history | ✔ |  |
| P1-4.08 | 4 | שינוי ימי אימון | PASS | PlanDetailScreen | R: SeederAndPlanTest.workouts can be reordered and rescheduled | ✔ |  |
| P1-4.09 | 4 | שמירת תוכנית כתבנית | PASS | 'שמור כתבנית' / 'צור תוכנית מהתבנית' | R: SeederAndPlanTest.plans can be saved as a template and instantiated again | ✔ |  |
| P1-4.10 | 4 | שינוי בתוכנית לא משנה אימונים היסטוריים | PASS | SessionFactory (snapshot), session.planId רך | D: SessionFactoryTest.later plan edits do not change an existing session snapshot; R: WorkoutRepositoryTest.changing the plan afterwards does not alter an existing session; SeederAndPlanTest.deleting a plan keeps workout history | ✔ |  |
| P1-4.11 | 4 | תוכנית ברירת מחדל: 4 אימונים (Upper A / Lower A / Upper B / Lower B) עם התרגילים, הסטים והחזרות המדויקים | PASS | domain/library/DefaultPlan.kt | D: DefaultPlanTableTest (טבלת הפרומפט מוקלדת ידנית ומושווית שורה-שורה), LibraryAndPlanTest.default plan matches the requested program; R: SeederAndPlanTest.first launch seeds... | ✔ | 'Pull-Up / Lat Pulldown' ו-'Hack Squat / Leg Press': נבחרה אפשרות אחת, האחרת בהערה |
| P1-4.12 | 4 | חלוקת שבוע: א' Upper A, ב' Lower A, ג' מנוחה, ד' Upper B, ה' Lower B | PASS | DefaultPlan (weekday 7,1,3,4) | D: DefaultPlanTableTest.the week is Sunday Monday rest Wednesday Thursday; NextWorkoutResolverTest.on the rest day tuesday... | ✔ |  |
| P1-4.13 | 4 | כל פרטי תוכנית ברירת המחדל ניתנים לעריכה/מחיקה | PASS | התוכנית נשמרת כתוכנית רגילה במסד | R: SeederAndPlanTest.a deleted default plan is never recreated; create/edit tests | ✔ |  |
| P1-5.01 | 5 | טיימר זמן אימון | PASS | ExerciseCard/WorkoutScreen (ElapsedText), Notifications.workoutOngoing (chronometer) | R: ServiceBehaviorTest.the_ongoing_notification_shows_the_rest_countdown_then_the_workout; U: AppSmokeTest | ✔ | הצגה חיה על מסך אמיתי לא נבדקה |
| P1-5.02 | 5 | רשימת תרגילים, מספר סטים וטווחי חזרות | PASS | WorkoutScreen, ExerciseCard | D: SessionFactoryTest.new session is active and snapshots plan data; U: AppSmokeTest | ✔ |  |
| P1-5.03 | 5 | נתוני האימון הקודם מוצגים | PASS | ExerciseCard (previous), WorkoutRepository.previousPerformance | R: WorkoutRepositoryTest.the next workout of the same type is pre-filled from the last one; an exercise never done before has no previous data | ✔ |  |
| P1-5.04 | 5 | טעינה מראש של משקלי העבודה הקודמים | PASS | SessionFactory.fromPlan | D: SessionFactoryTest.sets are pre-filled from the previous performance by index; without history the sets are empty | ✔ |  |
| P1-5.05 | 5 | שורת סט: סט / משקל / חזרות / RIR / הושלם | PASS | ExerciseCard.SetRow | U: AppSmokeTest.a_workout_can_be_started_and_a_set_logged... (60×8, סימון, שמירה במסד); ScenarioATest A5-A8 (משקל 60, חזרות 8, RIR 2 -> מסד) | ✔ |  |
| P1-5.06 | 5 | עדכון משקל, חזרות ו-RIR | PASS | WorkoutRepository.setWeight/setReps/setRir | R: WorkoutRepositoryTest.updating weight reps and rir is saved immediately; out of range values are ignored; U: ScenarioATest A5-A7 | ✔ |  |
| P1-5.07 | 5 | סימון סט כהושלם וביטול הסימון | PASS | toggleSetCompleted | R: a set without reps cannot be completed; D: SetEditingTest.completion toggles the timestamp; U: AppSmokeTest | ✔ |  |
| P1-5.08 | 5 | הוספה ומחיקה של סטים | PASS | addSet/deleteSet | R: adding and deleting sets keeps order consistent; a new set copies the previous set values but is not completed | ✔ |  |
| P1-5.09 | 5 | הפרדה בין סטי חימום לסטי עבודה | PASS | SetType.WARMUP | R: warm-up sets are separate from working sets and never trigger the rest timer; D: VolumeCalculatorTest.warm-up sets and incomplete sets are excluded | ✔ |  |
| P1-5.10 | 5 | הוספת תרגיל תוך כדי אימון | PASS | addExercise | R: an exercise can be added during the workout | ✔ |  |
| P1-5.11 | 5 | החלפת תרגיל | PASS | replaceExercise | R: an exercise can be replaced and keeps its position; replacing keeps sets that were already completed | ✔ |  |
| P1-5.12 | 5 | שינוי סדר התרגילים | PASS | reorderExercises | R: exercises can be reordered during the workout | ✔ | מחוות הגרירה עצמה לא נבדקה (ראה P1-4.04) |
| P1-5.13 | 5 | הערות לתרגיל והערות כלליות לאימון | PASS | setExerciseNotes/setSessionNotes | R: notes ratings and skip reason are stored | ✔ |  |
| P1-5.14 | 5 | דירוג אנרגיה והרגשה | PASS | setRatings, SessionDetailsCard | R: notes ratings and skip reason are stored | ✔ |  |
| P1-5.15 | 5 | תיעוד תרגילים חד-צדדיים | PASS | Side RIGHT/LEFT | D: SetEditingTest (3 בדיקות unilateral), SessionFactoryTest.unilateral exercise creates right and left rows; R: unilateral exercise rows come in right-left pairs | ✔ |  |
| P1-5.16 | 5 | טיימר מנוחה לפי זמן המנוחה של התרגיל | PASS | RestTimer, toggleSetCompleted | R: completing a working set starts the rest timer with the exercise's rest time; auto rest can be switched off; D: RestTimerTest(7) | ✔ |  |
| P1-5.17 | 5 | עצירה, המשך, +15/+30 שניות, דילוג | PASS | RestBar.kt, WorkoutRepository.pauseRest/resumeRest/addRestSeconds/skipRest | R: rest timer can pause resume add time and skip; U: AppSmokeTest (פס המנוחה עם '15' ו'דלג על המנוחה') | ✔ |  |
| P1-5.18 | 5 | התראה/רטט בסיום מנוחה - לוגיקה (פעם אחת, לפי הגדרה והרשאה) | PASS | RestFinisher.kt, Notifications.kt, Haptics.kt | R: ServiceBehaviorTest (4 בדיקות: פעם אחת, זמן סיום מיושן, הגדרה כבויה, בלי הרשאה); WorkoutRepositoryTest.exactly one finisher wins | ✔ |  |
| P1-5.19 | 5 | התראה/רטט בסיום מנוחה - מסירה בפועל במכשיר אמיתי | BLOCKED | WorkoutService, RestAlarmReceiver | X: דורש מכשיר Samsung + OS אמיתי | — | NOT VERIFIED: אין מכשיר/אמולטור בסביבה. נדרש: בדיקה ידנית (checklist ב-TESTING.md) |
| P1-5.20 | 5 | הטיימר פועל נכון בנעילת מסך/רקע - מנגנון (חותמות זמן מוחלטות + שירות + התראה גיבוי) | PASS | RestTimer (timestamps), WorkoutService, RestAlarmReceiver | D: RestTimerTest.survives process death...; R: ServiceBehaviorTest.the_backup_rest_alarm_is_scheduled_for_the_end_time_and_can_be_cancelled; WorkoutRepositoryTest.exactly one finisher wins | ✔ |  |
| P1-5.21 | 5 | הטיימר פועל נכון בנעילת מסך/רקע - התנהגות Samsung בפועל (Doze, חיסכון בסוללה) | BLOCKED | WorkoutService (foreground), setAndAllowWhileIdle | X | — | NOT VERIFIED: נדרש מכשיר. סיכון ידוע: מנהלי סוללה אגרסיביים; האפליקציה אינה מבקשת SCHEDULE_EXACT_ALARM |
| P1-5.22 | 5 | שמירה אוטומטית של כל שינוי | PASS | כל ה-setters כותבים מיד ל-Room | R: updating weight reps and rir is saved immediately; U: AppSmokeTest (המשקל והחזרות נקראים ישירות מהמסד אחרי הקלדה) | ✔ |  |
| P1-5.23 | 5 | שחזור אימון פעיל אחרי יציאה/סגירה לא צפויה | PASS | observeActive + באנר/כפתור 'המשך אימון' | R: WorkoutRepositoryTest.an active workout survives the process being killed and is fully restorable; DurabilityTest.data written before a restart is still there; U: ScreenSweepTest.a_running_workout_is_offered_for_continuing_on_home | ✔ | שיחה נכנסת/הרג תהליך אמיתי: לא נבדקו על מכשיר |
| P1-5.24 | 5 | מסך סיכום: משך, תרגילים, סטים, משקלים/חזרות, נפח, הערות, השוואה לאימון קודם | PASS | SummaryScreen.kt | U: AppFlowsTest.finishing_a_workout_opens_the_summary... (נפח 480); D: ProgressTest.comparison reports volume change versus previous session | ✔ |  |
| P1-6.01 | 6 | לפחות 100 תרגילים | PASS | app/src/main/assets/library/*.json (104) | D: LibraryAndPlanTest.library has at least 100 exercises; U: AppSmokeTest ('104 תרגילים') | ✔ |  |
| P1-6.02 | 6 | לכל תרגיל: שם עברית+אנגלית, שרירים ראשי/משני, ציוד, הוראות שלב-שלב, טעויות, בטיחות, חלופות | PASS | ExerciseLibrary.validate | D: LibraryAndPlanTest.library passes every consistency rule (דורש ≥3 שלבים, ≥2 טעויות, בטיחות, חלופות קיימות, שם בשתי שפות) | ✔ |  |
| P1-6.03 | 6 | בלי קישורי יוטיוב מומצאים; חיפוש ממוקד כשאין סרטון מאומת | PASS | YouTube.kt, ExerciseDetailScreen | D: built-in exercises never carry an invented video link; youtube search link is a search; U: AppFlowsTest.library_row_opens_the_exercise_page_with_honest_video_search | ✔ |  |
| P1-6.04 | 6 | חיפוש וסינון לפי קבוצת שרירים, ציוד ושם | PASS | ExerciseSearch.kt, LibraryScreen | D: ExerciseSearchTest(8); U: AppSmokeTest.library_search_filters_the_list | ✔ |  |
| P1-6.05 | 6 | תרגילים אישיים עם שם, הסבר וקישור | PASS | ExerciseEditScreen, ExerciseRepository | R: SeederAndPlanTest.custom exercise lifecycle; reseeding the library never touches custom exercises | ✔ | טופס היצירה עצמו לא נבדק ב-UI |
| P1-7.01 | 7 | כל אימון שהסתיים נשמר במסד | PASS | finishWorkout | R: finishing removes unfinished sets...; history list totals match the domain volume calculation | ✔ |  |
| P1-7.02 | 7 | פתיחת אימון קודם: תאריך/שעה, סוג, משך, תרגילים, סטים, משקלים, RIR, הערות, נפח | PASS | HistoryScreen, SummaryScreen | U: ScreenSweepTest.a_workout_from_the_history_opens_its_summary_and_the_ai_screen; AppFlowsTest.finishing_a_workout... | ✔ |  |
| P1-7.03 | 7 | עריכת אימון אחרי שהסתיים (תיקון משקל גם שבוע אחרי) | PASS | SummaryScreen 'ערוך אימון' -> WorkoutScreen במצב עריכה | R: WorkoutRepositoryTest.a wrong weight can be corrected after the workout and volume follows; D: VolumeCalculatorTest.editing a historical weight...; U: ScreenSweepTest.a_finished_workout_can_be_opened_for_editing_from_its_summary | ✔ |  |
| P1-7.04 | 7 | הזנת אימון שבוצע בעבר עם תאריך היסטורי | PASS | JournalScreen ('אימון שבוצע בעבר'), createPastWorkout | R: a past workout can be entered with a historical date and time; start and end time can be edited and the date follows | ✔ | זרימת ה-UI של 'אימון שבוצע בעבר' לא נבדקה; הלוגיקה כן |
| P1-8.01 | 8 | מדידות: משקל, מותניים, חזה, זרוע, ירך, אגן, צוואר, אחוז שומן, תאריך, הערות | PASS | BodyScreen, MeasurementRepository | R: MeasurementTest.all metrics are stored and a note only entry is allowed | ✔ |  |
| P1-8.02 | 8 | לא חובה למלא את כל השדות | PASS | MeasurementInput (כל שדה nullable) | R: MeasurementTest.not every metric has to be entered | ✔ |  |
| P1-8.03 | 8 | עריכה ומחיקה של מדידות | PASS | MeasurementSheet | R: a measurement can be edited and its date moved; ... deleted and stays gone; U: AppFlowsTest.a_body_measurement_can_be_added_from_the_body_tab | ✔ |  |
| P1-8.04 | 8 | המדידות נשמרות לצמיתות עד מחיקה יזומה | PASS | Room (קובץ DB) | R: DurabilityTest.data written before a restart is still there after it; BackupRepositoryTest | ✔ |  |
| P1-9.01 | 9 | מודל נתונים: Exercise, WorkoutPlan, PlannedExercise, WorkoutSession, SessionExercise, ExerciseSet, BodyMeasurement, ActivityEvent, AppSettings | PASS | app/schemas/.../2.json, DATA_MODEL.md | B: סכימה מיוצאת; R: DurabilityTest.the exported schema for the current version is committed | ✔ |  |
| P1-9.02 | 9 | מפתחות יציבים, קשרי גומלין, אינדקסים וטרנזקציות | PASS | Daos.kt, db.withTransaction | R: DurabilityTest.foreign keys are enforced by the database; BackupRepositoryTest.a failure in the middle of an import rolls everything back | ✔ |  |
| P1-9.03 | 9 | מיגרציות בלי מחיקת נתונים | PASS | Migrations.kt (MIGRATION_1_2), בלי fallbackToDestructiveMigration | R: MigrationTest.upgrading from version 1 keeps every row...; DurabilityTest.there is a migration for every version step; an unexpected newer database version fails safely | ✔ |  |
| P1-10.01 | 10 | ייצוא כל הנתונים ל-JSON עם גרסת Schema ברורה | PASS | BackupCodec, BackupRepository | D: BackupTest.backup declares an explicit schema version and format; R: BackupRepositoryTest.export then restore into a fresh install gives identical data | ✔ |  |
| P1-10.02 | 10 | ייבוא מגיבוי JSON | PASS | BackupRepository.import | R: BackupRepositoryTest (10 בדיקות) | ✔ |  |
| P1-10.03 | 10 | ייצוא אימונים ל-CSV | PASS | CsvExporter, BackupSection ('אימונים ל-CSV') | D: ExportTest (csv); R: BackupArchiveTest/BackupRepositoryTest.csv and text exports can be produced from stored data | ✔ |  |
| P1-10.04 | 10 | ייצוא אימון בודד | PASS | SessionJson, AiExportScreen (scope=SINGLE) | D: ExportTest.single workout json round trips including the previous workout; PeriodExportTest | ✔ | הכפתורים ב-UI לא נלחצו ב-UI test |
| P1-10.05 | 10 | העתקת סיכום אימון כטקסט | PASS | SummaryScreen 'העתק סיכום קצר' | D: ExportTest.workout text contains the essentials; U: AppFlowsTest (לוח) | ✔ |  |
| P1-10.06 | 10 | שיתוף קבצים דרך Android Share Sheet - בניית ה-Intent | PASS | ShareHelper.shareText/shareFile/shareBytes + FileProvider | R: ShareIntentsTest (כתובת content://, הרשאת קריאה, BOM) | ✔ |  |
| P1-10.07 | 10 | שיתוף קבצים - חלון הבחירה של המערכת והאפליקציות המקבלות | BLOCKED | Intent.createChooser | X | — | NOT VERIFIED: דורש מכשיר |
| P1-10.08 | 10 | בחירת קובץ מ-Google Drive דרך ממשק הקבצים של Android | PARTIAL | BackupSection (OpenDocument / CreateDocument) | C | — | NOT VERIFIED: בורר הקבצים הוא ממשק מערכת; הקוד משתמש ב-Storage Access Framework (ללא הרשאת אחסון) |
| P1-10.09 | 10 | אימות קובץ לפני ייבוא | PASS | BackupValidator | D: BackupTest (invalid input, newer schema, duplicate ids, dangling references, implausible values); R: an invalid or foreign file is rejected before anything changes | ✔ |  |
| P1-10.10 | 10 | אישור לפני דריסת נתונים + גיבוי בטיחות | PASS | BackupRepository (REPLACE עם אישור + safety copy), BackupSection דיאלוג | R: restoring over existing data replaces it and writes a safety copy first; BackupArchiveTest | ✔ | דיאלוג האישור עצמו נבדק בקוד בלבד |
| P1-10.11 | 10 | כפתור 'העתק דוח לניתוח ב-ChatGPT' | PASS | SummaryScreen + SummaryViewModel.aiReportText (הוחזר בביקורת) | U: AppFlowsTest.finishing_a_workout... (הלוח מכיל את הדוח וההוראה בסופו); ScreenSweepTest.the_quick_copy_report... | ✔ | פער G4 תוקן |
| P1-10.12 | 10 | הדוח מכיל את נתוני האימון ואת האימון הקודם | PASS | TextReport.aiReport | D: ExportTest.ai report includes previous workout, comparison and the exact request; ai report says plainly when there is no previous workout | ✔ |  |
| P1-10.13 | 10 | אין צורך ב-API בתשלום | PASS | אין הרשאת INTERNET | R: ManifestPolicyTest (בלי INTERNET, בלי קוד רשת) | ✔ |  |
| P1-11.01 | 11 | בדיקות לוגיקה: יצירת תוכנית, הוספת תרגיל, עדכון סט, שמירת אימון, שחזור אימון פעיל, נפח, מדידות, ייצוא/ייבוא, שמירת נתונים בין גרסאות | PASS | domain/src/test, app/src/test | ראה P1-4.*, P1-5.*, P1-8.*, P1-9.*, P1-10.* | ✔ |  |
| P1-11.02 | 11 | הרצת הבדיקות בפועל | PASS | GitHub Actions android.yml | B: ריצת CI (ראה FINAL_ACCEPTANCE_REPORT.md) | ✔ |  |
| P1-11.03 | 11 | האפליקציה מתקמפלת והמסכים המרכזיים עובדים | PASS | Gradle build + Compose UI tests | B + U: AppSmokeTest, AppFlowsTest, ScreenSweepTest | ✔ | 'עובדים' = ב-Robolectric על JVM; לא נבדק על מכשיר |
| P1-12.01 | 12 | פרויקט Android תקין הניתן לפתיחה ב-Android Studio | PASS | settings.gradle.kts, gradle wrapper | B: ה-Gradle wrapper בונה את הפרויקט ב-CI | ✔ | NOT VERIFIED: לא נפתח ב-Android Studio בפועל |
| P1-12.02 | 12 | Gradle Build עם תיקון שגיאות | PASS | CI | B | ✔ |  |
| P1-12.03 | 12 | Debug APK מוכן להתקנה | PASS | app/build/outputs/apk/debug | B: ארטיפקט liftos-debug-apk + aapt/sha256 בלוג CI | ✔ | התקנה בפועל על המכשיר: NOT VERIFIED |
| P1-12.04 | 12 | Release APK והסבר חתימה | PARTIAL | app/build.gradle.kts (R8 + keystore.properties), README.md | B: assembleRelease עובר | ✔ | נחתם במפתח ה-debug המשותף כשאין keystore.properties; זו אינה חתימת הפצה. התנהגות R8 בזמן ריצה: NOT VERIFIED |
| P1-12.05 | 12 | אין לטעון שקיים APK שלא נבנה | PASS | תהליך | B: הארטיפקטים נבדקים דרך GitHub API (digest) ובלוג CI | ✔ |  |
| P1-13.01 | 13 | README, ARCHITECTURE, DATA_MODEL, DEVELOPMENT_PLAN, CHANGELOG, CLAUDE.md | PASS | קבצי השורש | C: הקבצים קיימים ומעודכנים (נבדק ידנית בביקורת) | ✔ |  |
| P1-13.02 | 13 | שימוש ב-Git | PASS | git log | C | ✔ |  |
| P1-14.01 | 14 | לפתח בפועל, לא רק תוכנית; לא למחוק יכולות עובדות | PASS | היסטוריית git | C: מספר הבדיקות רק עלה (95 -> ראה דוח) | ✔ |  |
| P1-14.02 | 14 | דיווח בסיום חלק 1 (מה עובד, מה עבר, איפה ה-APK, מה נשאר) | PASS | README.md, CHANGELOG.md, DEVELOPMENT_PLAN.md | C | ✔ |  |

## 4. מטריצה - פרומפט 2 (חלק 2)

| ID | § | דרישה | סטטוס | קוד | אימות | תוצאה | פער / פעולה |
|---|---|---|---|---|---|---|---|
| P2-0.01 | חזון | מיקוד בחדר כושר בלבד; אין מודול תזונה/קלוריות/ארוחות | PASS | אין קוד תזונה בפרויקט | R: ManifestPolicyTest.there is no nutrition module (סריקת מקורות ושמות קבצים) | ✔ | היעדר תזונה אינו פער. המילה 'ארוחה' מופיעה רק כנסיבת מדידת משקל (פרומפט 2 §7) |
| P2-1.01 | 1 | המשך על הפרויקט הקיים (ללא פרויקט חדש), שמירת יכולות ונתונים קיימים | PASS | היסטוריית git מחלק 1 | R: MigrationTest (נתוני v1 נשמרים); בדיקות חלק 1 ממשיכות לעבור | ✔ |  |
| P2-1.02 | 1 | הפרדה בין הושלם/חלקי/טרם פותח | PASS | FEATURES.md, DEVELOPMENT_PLAN.md | C | ✔ |  |
| P2-1.03 | 1 | שינוי מסד נתונים רק עם Migration שמור נתונים | PASS | MIGRATION_1_2 (תוספתי בלבד) | R: MigrationTest.upgrading from version 1 keeps every row and adds sensible defaults; DurabilityTest | ✔ |  |
| P2-2.01 | 2 | המשקל הגבוה ביותר שהורם, לכל תרגיל | PASS | ExerciseAnalytics.overview.maxWeight | D: ExerciseAnalyticsTest.overview separates logged singles from estimates and finds rep maxes | ✔ |  |
| P2-2.02 | 2 | מספר חזרות בכל משקל | PASS | ExerciseOverview.weightTable | D: ExerciseAnalyticsTest.weight table groups reps per weight; U: ScreenSweepTest.the_exercise_screen_shows... ('חזרות בכל משקל') | ✔ |  |
| P2-2.03 | 2 | משקל ממוצע לסטי עבודה (אם שימושי) | PASS | avgWorkingWeightKg + ExerciseAnalyticsScreen (הוצג בביקורת) | D: ExerciseAnalyticsTest.average working weight is weighted by reps when weights differ; U: ScreenSweepTest.the_exercise_screen_shows_average_working_weight... | ✔ | פער G1 תוקן בביקורת |
| P2-2.04 | 2 | שיאים אישיים בעמוד התרגיל | PASS | ExerciseAnalyticsScreen (BestTiles) | D: RecordsTest; U: ScreenSweepTest (עמוד תרגיל) | ✔ |  |
| P2-2.05 | 2 | Estimated 1RM (Epley) מסומן במפורש כהערכה | PASS | OneRepMax.kt, BestTiles ('1RM משוער (הערכה)') | D: OneRepMaxTest.epley formula; refuses unreliable or invalid inputs; ExerciseAnalyticsTest.overview separates logged singles from estimates | ✔ |  |
| P2-2.06 | 2 | הערכת 1RM לא מוצגת כשיא שנמדד; Actual 1RM רק אם בוצעה חזרה בודדת | PASS | ExerciseOverview.actualOneRm; טקסט 'לא תועדה חזרה בודדת' | D: ExerciseAnalyticsTest.no actual 1RM is reported when no single was logged | ✔ |  |
| P2-2.07 | 2 | משקל מקסימלי ל-5, 8, 10 חזרות | PASS | ExerciseOverview.repMax; BestTiles | D: ExerciseAnalyticsTest.overview separates logged singles from estimates and finds rep maxes; RecordsTest.rep max records use the heaviest weight for at least n reps | ✔ | שיאי ה-PR עצמם הם 5 ו-10 (הפרומפט §5 מבקש 5/10); 8 מוצג באנליטיקת התרגיל |
| P2-2.08 | 2 | התקדמות ביחס לאימון הקודם | PASS | ExerciseAnalytics.lastVsPrevious, Header | D: ExerciseAnalyticsTest.last versus previous gives a verdict; ProgressTest.identical performance is reported as the same - never as improvement | ✔ |  |
| P2-2.09 | 2 | התקדמות לאורך חודש, 3 חודשים ושנה | PASS | ExerciseAnalytics.TREND_WINDOWS, TrendCard | D: ExerciseAnalyticsTest.trend needs two workouts in the window and reports first versus last | ✔ |  |
| P2-2.10 | 2 | היסטוריית כל הסטים שבוצעו | PASS | ExerciseAnalyticsScreen ('היסטוריית הסטים') | U: ScreenSweepTest.the_exercise_screen_shows_average_working_weight... | ✔ |  |
| P2-2.11 | 2 | משקלים מומלצים לאימון הבא | PASS | ProgressionEngine + SuggestionCard | D: ProgressionEngineTest(19) | ✔ | ראה P2-4.* |
| P2-2.12 | 2 | עמוד מפורט לתרגיל נבחר (למשל Bench Press) | PASS | ExerciseAnalyticsScreen | U: ScreenSweepTest.the_exercise_screen_shows_average_working_weight_and_the_volume_of_each_set | ✔ |  |
| P2-2.13 | 2 | Volume Load = משקל × חזרות; נפח לכל סט / תרגיל / אימון | PASS | SetFact.volumeKg, VolumeCalculator, ExerciseSessionStat | D: VolumeCalculatorTest(6); ExerciseAnalyticsTest.session stats count volume...; U: AppFlowsTest (480) | ✔ | נפח לכל סט מוצג בעמוד התרגיל (תוקן בביקורת, G2) |
| P2-2.14 | 2 | נפח שבועי וחודשי; מספר סטי עבודה; השוואה לתקופה קודמת | PASS | VolumeAnalytics.buckets/periodStats/compare | D: VolumeAnalyticsTest(9): period stats, comparison against the previous equal period, buckets keep empty weeks | ✔ |  |
| P2-2.15 | 2 | סטים ואימונים לכל קבוצת שרירים, עם הצגת הנחות המודל | PASS | VolumeAnalytics.muscleSets, MuscleModel.ASSUMPTIONS, AnalyticsScreen | D: VolumeAnalyticsTest.direct and indirect muscle sets follow the stated model; warm-ups never count; U: AnalyticsScreen מציג ASSUMPTIONS | ✔ |  |
| P2-2.16 | 2 | הבחנה בין נפח בק"ג-חזרות לסטים אפקטיביים; זוג שמאל+ימין = סט אחד | PASS | SetFact.effectiveSets | D: ExerciseAnalyticsTest.a unilateral pair counts as one set but volume sums both sides | ✔ |  |
| P2-2.17 | 2 | דשבורד: אימונים השבוע, סטים, נפח, שיאים חדשים, שרירים שאומנו, עמידה בתוכנית, מגמת כוח, זמן אימון ממוצע | PASS | Dashboard.kt, AnalyticsScreen.Dashboard | D: DashboardTest(6); U: ScreenSweepTest.the_progress_tab_shows_charts_muscles_insights_and_the_exercise_list | ✔ |  |
| P2-2.18 | 2 | הכול מחושב בזמן אמת מהמסד; אין נתוני דמו | PASS | AppContainer.analyticsData (נגזר, לא נשמר) | R: AnalyticsLiveUpdateTest (5); D: DashboardTest.empty database gives empty cards, not made-up numbers | ✔ |  |
| P2-3.01 | 3 | גרפי אימונים: משקל עבודה לכל תרגיל, 1RM משוער לאורך זמן, חזרות במשקל קבוע | PASS | ChartSeries.metricPoints/repsAtWeight; ExerciseAnalyticsScreen | D: ChartSeriesTest.metric points carry exact value, date and workout; reps at a fixed weight use the best set of each workout | ✔ | הציור עצמו (Canvas) לא נבדק חזותית |
| P2-3.02 | 3 | גרפי אימונים: נפח לכל אימון, נפח שבועי, סטי עבודה, אימונים בשבוע, משך אימון | PASS | ChartChoice (6 גרפים) | D: ChartSeriesTest.bucket series...; workout volume points skip workouts without volume; U: ScreenSweepTest.every_chart_choice_can_be_selected | ✔ |  |
| P2-3.03 | 3 | חלוקת אימונים לפי קבוצות שרירים | PASS | DistributionBars | D: ChartSeriesTest.distribution fractions add up to one | ✔ |  |
| P2-3.04 | 3 | התקדמות לעומת תקופות קודמות | PASS | AnalyticsScreen.ComparisonCard | D: VolumeAnalyticsTest.comparison against the previous equal period | ✔ |  |
| P2-3.05 | 3 | גרפי גוף: משקל, ממוצע נע 7 ימים, היקפים, אחוז שומן | PASS | BodyScreen (גרף לכל שדה), BodyAnalytics | D: BodyAnalyticsTest.series keeps only measurements that have the field; ProgressTest.moving average smooths daily noise | ✔ |  |
| P2-3.06 | 3 | השוואת שתי תקופות במדדי גוף | PASS | BodyAnalytics.comparePeriods; BodyScreen ('30 הימים האחרונים מול 30 שלפניהם') | D: BodyAnalyticsTest.period averages are compared | ✔ |  |
| P2-3.07 | 3 | מסנני תקופה: 7/30/90 ימים, 6 חודשים, שנה, כל התקופה, טווח מותאם | PASS | Periods.kt, PeriodSelector | D: VolumeAnalyticsTest.periods resolve to concrete ranges; granularity grows with the range | ✔ | בחירת טווח מותאם דרך בורר תאריכים: לא נבדקה ב-UI |
| P2-3.08 | 3 | לחיצה על נקודה בגרף מציגה ערך מדויק, תאריך ואימון | PARTIAL | Charts.kt (InteractiveChart, ChartPoint.title/detail/sessionId) | D: ChartSeriesTest (הנתונים לנקודה: ערך, תאריך, אימון); C: ההקשבה ללחיצה | ✔ / X | NOT VERIFIED: הלחיצה על Canvas לא נבדקה ב-UI test |
| P2-3.09 | 3 | הגרפים מתעדכנים מיד בעקבות שינוי באימון/מדידה | PASS | נתונים נגזרים ב-Flow (analyticsData) | R: AnalyticsLiveUpdateTest (עריכת סט, ביטול סימון, מחיקת אימון, flow מול קריאה חד-פעמית) | ✔ |  |
| P2-3.10 | 3 | אין מספיק נתונים -> הודעה ולא גרף פיקטיבי | PASS | InteractiveChart.emptyMessage, minPoints | D: DashboardTest.no trend is claimed without data in both windows; U: AppSmokeTest ('עוד אין נתוני אימונים') | ✔ |  |
| P2-4.01 | 4 | Double Progression כשיטה מרכזית (הדוגמה מהפרומפט -> 62.5 ק"ג) | PASS | ProgressionEngine.kt | D: ProgressionEngineTest.the double progression example from the brief suggests 62_5 kg | ✔ |  |
| P2-4.02 | 4 | התחשבות בטווח חזרות, סטים, משקל, RIR/RPE, היסטוריה, כישלון, ירידה, סוג תרגיל, קפיצת משקל, אנרגיה, עייפות, תדירות | PASS | ProgressionEngine (+ProgressionModels) | D: ProgressionEngineTest (before top range, failure, declines, increments by equipment, low energy, long break, missing sets, pyramids, bodyweight, holds) | ✔ |  |
| P2-4.03 | 4 | המלצות מילוליות (שקול העלאה / שמור משקל והוסף חזרה / בדוק התאוששות) | PASS | ProgressionSuggestion.explanation | D: ProgressionEngineTest.before the top of the range the advice is to add reps...; two consecutive declines ask to check recovery | ✔ |  |
| P2-4.04 | 4 | כל המלצה כוללת הסבר | PASS | ProgressionSuggestion | D: ProgressionEngineTest.every kind of suggestion carries an explanation | ✔ |  |
| P2-4.05 | 4 | אפשר להתעלם מהמלצה | PASS | ProgressionRepository (decision IGNORED), SuggestionCard | D: ProgressionEngineTest.an ignored suggestion stays hidden only until the next workout | ✔ | לחיצת 'התעלם' ב-UI לא נבדקה |
| P2-4.06 | 4 | שינוי משקל יעד ידני + מדרגת משקל לתרגיל | PASS | SuggestionSheet, ExerciseAnalyticsScreen (מדרגת משקל), ExercisePrefs | D: ProgressionEngineTest.increments follow the equipment and can be overridden; plan targets override the snapshotted ones | ✔ |  |
| P2-4.07 | 4 | אין העלאת משקל אוטומטית ללא אישור | PASS | WorkoutViewModel.applyWeight נקרא רק מכפתור 'החל X ק״ג על הסטים שנותרו' ב-SuggestionSheet | D: SessionFactoryTest.sets are pre-filled from the previous performance by index (ערכי האימון הקודם, לא ההצעה); C: ניתוח נתיבי הקוד - applyWeightToOpenSets מופעל רק ממסלול המשתמש | ✔ | הוכחה בניתוח קוד + בדיקת הטעינה מראש; לחיצת הכפתור לא נבדקה ב-UI |
| P2-4.08 | 4 | אימון בודד גרוע לא מוביל למסקנה שהמשתמש נחלש | PASS | ProgressionEngine (דורש 2 ירידות) | D: ProgressionEngineTest.one bad workout never lowers the target; a deliberate weight reduction is not treated as a decline | ✔ |  |
| P2-4.09 | 4 | בלי מספיק נתונים - המלצה מסומנת כביטחון נמוך / אין המלצה | PASS | Confidence + InfoChip | D: ProgressionEngineTest.a single workout is a low-confidence suggestion that says so; no history gives no recommendation | ✔ |  |
| P2-5.01 | 5 | שיאים: משקל מקסימלי, 1RM בפועל, 5 חזרות, 10 חזרות, 1RM משוער, חזרות במשקל, נפח לתרגיל | PASS | Records.kt (RecordKind x9) | D: RecordsTest(12) | ✔ |  |
| P2-5.02 | 5 | האימון הראשון הוא בסיס, תיקו אינו שיא | PASS | PersonalRecordEngine | D: RecordsTest.the first workout creates a baseline, not a record; a heavier weight is a record and a tie is not | ✔ |  |
| P2-5.03 | 5 | חימום אינו קובע שיא | PASS | PersonalRecordEngine | D: RecordsTest.warm-ups cannot set records | ✔ |  |
| P2-5.04 | 5 | הודעה 'שיא אישי חדש!' לא מוגזמת + גביע ליד התרגיל | PASS | SummaryScreen (רשימת שיאים), RecordText, Notifications.newRecord | D: RecordsTest.live detection compares the active workout against earlier history only; headline keeps the most important kinds | ✔ | הצגה חזותית לא נבדקה |
| P2-5.05 | 5 | מסך שיאים מרכזי עם פתיחת שיא לאימון שבו נקבע | PASS | RecordsScreen | U: ScreenSweepTest.the_shortcuts_open_records_goals_and_recovery | ✔ |  |
| P2-5.06 | 5 | עריכת היסטוריה מחשבת מחדש שיאים בעקביות | PASS | שיאים נגזרים מהסטים | D: RecordsTest.editing history recomputes consistently; R: AnalyticsLiveUpdateTest.editing a past set changes the volume and the record that workout set | ✔ |  |
| P2-6.01 | 6 | תצוגות: יומית, שבועית, חודשית, רשימה, היסטוריה | PASS | JournalScreen, CalendarViews | U: ScreenSweepTest.all_five_journal_views_open_and_the_history_lists_the_workouts | ✔ |  |
| P2-6.02 | 6 | סוגי אירועים (אימון מתוכנן/הושלם/הוחמץ, מנוחה, אירובי, הליכה, ריצה, שחייה, רכיבה, ספורט, מאמן, מדידת גוף, מותאם) | PASS | ActivityType, EventEditorSheet | D: CalendarTest.rest days and single events are classified; R: CalendarRepositoryTest | ✔ |  |
| P2-6.03 | 6 | יצירה/עריכה/מחיקה של אירוע | PASS | EventEditorSheet, ActivityRepository | R: CalendarRepositoryTest.activity events can be saved and removed; U: AppFlowsTest.an_event_added_in_the_journal_is_saved_and_shown | ✔ |  |
| P2-6.04 | 6 | שינוי תאריך/שעה/משך, הערות | PASS | EventEditorSheet | R: CalendarRepositoryTest.an event is stored with its local date and can be deleted | ✔ | שינוי תאריך מתבצע דרך העורך; לא נבדק ב-UI |
| P2-6.05 | 6 | אירועים חוזרים | PASS | Recurrence.kt | D: CalendarTest (weekly, every other week, daily with until/count, monthly clamping, exceptions, text round trip); R: marking one occurrence... leaves the rest of the series alone | ✔ |  |
| P2-6.06 | 6 | העברת אימון ליום אחר בלי לשנות היסטוריה | PASS | planned_overrides | D: CalendarTest.moving a planned workout shows it on the new day only; moving a plan day does not touch recorded workouts; R: moving a planned workout never touches recorded workouts | ✔ |  |
| P2-6.07 | 6 | סימון אימון שלא בוצע (מפורש, לא אוטומטי) | PASS | OverrideAction.MISSED/SKIPPED | D: CalendarTest.skipped and missed marks are explicit and never automatic; a recent past planned day without a workout is flagged as missed | ✔ |  |
| P2-6.08 | 6 | חיבור אירוע לאימון מתועד | PASS | linkOccurrence | R: CalendarRepositoryTest.linking an occurrence to a workout marks it done and can be undone | ✔ |  |
| P2-6.09 | 6 | צבעים/סימונים להבדלה בין מתוכנן, בוצע ויום מנוחה | PASS | CalendarStyle.kt, מקרא | D: CalendarTest.markers summarise each day for the month grid | ✔ | הצבעים עצמם לא נבדקו חזותית |
| P2-7.01 | 7 | שדות: משקל, מותניים, חזה, זרוע, ירך, אגן, צוואר, כתפיים, אחוז שומן, הערות, תאריך ושעה | PASS | BodyMeasurement (shouldersCm נוסף במיגרציה) | R: MeasurementTest(8); MigrationTest | ✔ |  |
| P2-7.02 | 7 | שדות מדידה מותאמים אישית | PASS | CustomMetric, MetricsSheet | R: BackupArchiveTest.a zip backup carries ... custom fields; D: BodyAnalyticsTest.custom metrics have their own series | ✔ |  |
| P2-7.03 | 7 | השוואת מדידות, שינוי מוחלט ובאחוזים | PASS | BodyAnalytics.compare, BodyCompare.kt | D: BodyAnalyticsTest.two measurements compare field by field with percent | ✔ |  |
| P2-7.04 | 7 | ממוצע משקל נע והשוואת תקופות | PASS | BodyAnalytics, Progress.movingAverage | D: ProgressTest.moving average smooths daily noise; BodyAnalyticsTest.period averages are compared | ✔ |  |
| P2-7.05 | 7 | עריכת מדידות ישנות ומחיקה לאחר אישור | PASS | MeasurementSheet, דיאלוג מחיקה | R: MeasurementTest (edit/delete) | ✔ | דיאלוג האישור לא נבדק ב-UI |
| P2-7.06 | 7 | תיעוד נסיבות המדידה | PASS | conditions (עמודה במיגרציה) | R: MeasurementTest.all metrics are stored...; MigrationTest (conditions נוספה) | ✔ |  |
| P2-7.07 | 7 | הבחנה בין תנודות יומיות למגמה ארוכת טווח; בלי מסקנות על שומן/שריר | PASS | BodyAnalytics.trend | D: BodyAnalyticsTest.a steady decline is a falling trend; daily noise without a direction is stable; too few readings give no trend | ✔ | הטקסט לא מסיק על שומן/שריר (C) |
| P2-7.08 | 7 | אזהרה עדינה על ערך חריג | PASS | BodyAnalytics.outlier | D: BodyAnalyticsTest.unusual values trigger a gentle warning | ✔ |  |
| P2-8.01 | 8 | צילום או בחירה מהגלריה | PARTIAL | PhotosScreen (TakePicture / PickVisualMedia), PhotoRepository.newCameraTarget | R: ShareIntentsTest לא רלוונטי; U: PhotoPipelineTest מכסה את קליטת הקובץ | ✔ / X | NOT VERIFIED: המצלמה ובורר הגלריה הם ממשקי מערכת. אין הרשאת CAMERA - נעשה שימוש באפליקציית המצלמה |
| P2-8.02 | 8 | סוגי צילום (חזית/צד/גב) + תאריך + משקל + הערות | PASS | PhotoAngle, ProgressPhoto | U: PhotoPipelineTest.details can be edited and a photo can be deleted with its file; ScreenSweepTest (תמונות ברשימה) | ✔ |  |
| P2-8.03 | 8 | הקטנה וקידוד JPEG, סיבוב EXIF, הסרת מטא-דאטה | PASS | PhotoRepository.decode/save | U: PhotoPipelineTest (הקטנה ל-≤2048, שמירת יחס, סיבוב EXIF, קובץ שאינו תמונה נדחה) | ✔ |  |
| P2-8.04 | 8 | מסך השוואה: זו לצד זו / לפני-אחרי / Slider | PARTIAL | PhotoCompareScreen.kt | U: ScreenSweepTest (כפתור 'השוואת תמונות' קיים); C | ✔ / X | NOT VERIFIED: מסך ההשוואה לא נפתח ב-UI test; מחוות ה-Slider לא נבדקה |
| P2-8.05 | 8 | שמירה פרטית; אין שליחה לשירות חיצוני | PASS | files/photos פרטי; אין INTERNET | R: ManifestPolicyTest; U: PhotoPipelineTest.a big photo is stored ... in private storage | ✔ |  |
| P2-8.06 | 8 | ייצוא התמונות כחלק מגיבוי מלא | PASS | BackupRepository.exportZip(includePhotos) | R: BackupArchiveTest.a zip backup carries photos goals and custom fields into a fresh install | ✔ |  |
| P2-9.01 | 9 | סוגי יעדים: משקל גוף, היקף, משקל בתרגיל, חזרות בתרגיל, אימונים בחודש/בשבוע, השלמת תוכנית, מותאם | PASS | GoalType x8, GoalEngine | D: GoalEngineTest(12) | ✔ |  |
| P2-9.02 | 9 | שדות יעד: שם, קטגוריה, ערך התחלתי/נוכחי/יעד, תאריכים, אחוז התקדמות, היסטוריית שינויים, סטטוס | PASS | Goal.kt, GoalRepository.log | D: GoalEngineTest; U: ScreenSweepTest (יעד מוצג); R: BackupV2Test.a version 2 backup round trips goals | ✔ |  |
| P2-9.03 | 9 | יצירת יעד והשלמת יעד (חיווי הצלחה) | PASS | GoalRepository.markNewlyAchieved, GoalTracker, Notifications.goalReached | D: GoalEngineTest.a goal is achieved when the target is reached in the right direction; newly achieved lists only active goals that reached the target | ✔ | מסירת ההתראה בפועל: NOT VERIFIED |
| P2-9.04 | 9 | אין התקדמות שאינה מגובה בנתונים | PASS | GoalEngine (progress=null כשאין נתון) | D: GoalEngineTest.no data means no progress, not zero percent | ✔ |  |
| P2-9.05 | 9 | מסך יעדים (יצירה/עריכה/השהיה/מחיקה) | PASS | GoalsScreen | U: ScreenSweepTest.the_shortcuts_open_records_goals_and_recovery | ✔ | יצירה/עריכה דרך הטופס לא נבדקו ב-UI |
| P2-10.01 | 10 | דירוגים לאחר אימון: אנרגיה, קושי, איכות, כאבי שרירים, עייפות, שינה, הערות - אופציונליים | PASS | SessionDetailsCard, WorkoutSession.*Rating | R: WorkoutRepositoryTest.notes ratings and skip reason are stored; MigrationTest (עמודות חדשות) | ✔ |  |
| P2-10.02 | 10 | ניתוחים: שינויי אנרגיה, תדירות, ימי מנוחה, עומס שבועי, מגמות עייפות | PASS | RecoveryAnalytics.kt, RecoveryScreen | D: RecoveryTest(8); U: ScreenSweepTest.the_shortcuts_open_records_goals_and_recovery | ✔ |  |
| P2-10.03 | 10 | הצעה לבחון Deload - בלי אבחנה רפואית ובלי קביעת אימון יתר | PASS | RecoveryAnalytics.deloadHint | D: RecoveryTest.a single signal does not suggest a deload; high fatigue and short sleep are reported as signals not diagnoses | ✔ |  |
| P2-10.04 | 10 | מסך קצר ושימושי, לא שאלון רפואי | PASS | RecoveryScreen | U: ScreenSweepTest | ✔ |  |
| P2-11.01 | 11 | כפתור מרכזי 'נתח את האימון עם AI' בסיכום האימון | PASS | SummaryScreen | U: AppFlowsTest.finishing_a_workout_opens_the_summary... | ✔ |  |
| P2-11.02 | 11 | העתקת דוח מפורט ללוח | PASS | AiExportScreen, ShareHelper.copy | R: ShareIntentsTest.copy puts the text on the clipboard; U: AppFlowsTest (לוח) | ✔ |  |
| P2-11.03 | 11 | שיתוף דרך Android Share Sheet | PASS | ShareHelper.shareText/shareFile | R: ShareIntentsTest | ✔ | חלון הבחירה: NOT VERIFIED (מערכת) |
| P2-11.04 | 11 | ייצוא JSON / CSV / TXT | PASS | ExportFormat, AiExportViewModel.payload | D: PeriodExportTest, ExportTest; R: BackupRepositoryTest.csv and text exports can be produced from stored data | ✔ |  |
| P2-11.05 | 11 | ייצוא PDF - הפקת קובץ | BLOCKED | PdfExport.kt (PdfDocument) | D: ReportBlocksTest (חלוקה לעמודים); U: PdfExportAttemptTest - נוסה בגרפיקה native ודולג (PdfDocument לא שמיש ב-Robolectric) | ✔ / דולג | NOT VERIFIED: ציור ה-PDF בפועל לא הורץ באף סביבה. נדרש: מכשיר/אמולטור |
| P2-11.06 | 11 | פתיחת ChatGPT/Claude באמצעות שיתוף נתמך - בניית ה-Intent ללא טענת שליחה | PASS | ShareHelper.shareTextTo/openApp + openAssistant (העתקה + שיתוף ישיר + נפילה לאתר) | R: ShareIntentsTest (שיתוף ישיר רק לחבילה היעד; חבילה לא מותקנת מחזירה false) | ✔ | פער G6 תוקן |
| P2-11.07 | 11 | פתיחת ChatGPT/Claude בפועל במכשיר | BLOCKED | queries ב-AndroidManifest | X | — | NOT VERIFIED: דורש מכשיר עם האפליקציות; לא ידוע אם האפליקציות מקבלות ACTION_SEND טקסט (נשמר fallback) |
| P2-11.08 | 11 | אין טענה שהמידע נשלח ישירות ל-AI כשהוא רק הועתק/שותף | PASS | הודעות openAssistant, טקסטי המסך | D: AiProviderTest.no provider is connected, so the app never claims to send data; U: AppFlowsTest ('האפליקציה לא שולחת שום מידע') | ✔ |  |
| P2-11.09 | 11 | תוכן הדוח: תוכנית, תאריך, התחלה/סיום, משך, תרגילים, סטי עבודה וחימום בנפרד, משקלים, חזרות, RIR/RPE, נפח, מנוחה, אנרגיה, הערות | PASS | TextReport.aiReport | D: ExportTest; WorkoutReportExtrasTest.recovery ratings appear when they were entered | ✔ |  |
| P2-11.10 | 11 | הדוח כולל השוואה לאימון קודם, שיאים חדשים ונתוני מגמות רלוונטיים | PASS | TextReport.aiReport + WorkoutReportContext (נוסף בביקורת) | D: WorkoutReportExtrasTest(6); R/U: ScreenSweepTest.the_quick_copy_report_of_a_workout_has_its_records_its_trends_and_the_instruction | ✔ | פער 'High' שנמצא בביקורת ותוקן (הדוח הבודד לא כלל שיאים ומגמות) |
| P2-11.11 | 11 | נתוני גוף והערות אישיות רק לפי בחירה | PASS | ReportOptions (includeBody=false כברירת מחדל) | D: ExportTest.body data appears only when explicitly attached; PeriodReportTest.body data and notes only appear when chosen; PeriodExportTest.without notes removes every free text | ✔ |  |
| P2-11.12 | 11 | הוראת הבקשה ל-AI בנוסח הפרומפט, בסוף הדוח | PASS | TextReport.AI_INSTRUCTION | D: PeriodReportTest.the report ends with the exact analysis request; WorkoutReportExtrasTest.the exact request still ends the report | ✔ |  |
| P2-11.13 | 11 | ייצוא תקופתי: אימון בודד / שבוע / חודש / 12 שבועות / מותאם / כל ההיסטוריה | PASS | ReportScope | D: PeriodReportTest.scopes resolve to date ranges | ✔ |  |
| P2-11.14 | 11 | בחירה מה לכלול בדוח (גוף, הערות, התאוששות, יעדים, שיאים, הצעות) | PASS | ReportOptions + AiExportScreen | D: PeriodReportTest; U: ScreenSweepTest (מסך AI נפתח) | ✔ | המתגים עצמם לא נלחצו ב-UI test |
| P2-11.15 | 11 | שכבת אינטגרציית API עתידית (OpenAI/Anthropic): מפתח מאובטח, הסכמה, שליטה, כיבוי | FUTURE | ai/AiProvider.kt (ממשק + בקשה שדורשת הסכמה) | D: AiProviderTest.a request cannot be built without a recorded consent | ✔ | הכנה אדריכלית קיימת (ממשק + חובת הסכמה); אין מימוש, אין מפתחות, אין INTERNET |
| P2-12.01 | 12 | מנוע תובנות מקומי מבוסס חישוב (העלאת משקל, 4 אימונים בשבוע, נפח עלה, שריר שלא אומן, ירידה בביצועים, שיא חזרות) | PASS | InsightEngine.kt, AnalyticsScreen | D: InsightEngineTest(6) | ✔ |  |
| P2-12.02 | 12 | 'נדרשים אימונים נוספים כדי לזהות מגמה' כשאין מספיק נתונים | PASS | InsightEngine | D: InsightEngineTest.with too little history it says so and invents no trend | ✔ |  |
| P2-12.03 | 12 | לא לקרוא לחישובים AI | PASS | טקסטי התובנות | D: InsightEngineTest.no insight mentions artificial intelligence; U: AnalyticsScreen ('אין כאן בינה מלאכותית') | ✔ |  |
| P2-13.01 | 13 | תזכורות: אימון מתוכנן, שקילה, היקפים, סיכום שבועי, גיבוי - כל סוג בנפרד | PASS | ReminderPlanner, ReminderScheduler, RemindersSection | D: ReminderPlannerTest(15); R: ServiceBehaviorTest (switching on/off, one alarm each, content of weigh-in/weekly summary) | ✔ |  |
| P2-13.02 | 13 | סיום זמן מנוחה (מתג נפרד) | PASS | restNotificationEnabled | R: ServiceBehaviorTest.the_rest_alert_respects_the_notification_setting_but_still_ends_the_rest | ✔ |  |
| P2-13.03 | 13 | יעד שהושג ושיא אישי חדש - התראה לפי העדפות | PASS | GoalTracker.check, Notifications.goalReached/newRecord, WorkoutScreen (שיא ברקע) | R: ServiceBehaviorTest.reaching_a_goal_posts_one_notification_and_marks_it_achieved; the_goal_notification_has_its_own_switch...; a_new_record_notification_uses_the_achievements_text | ✔ | הפעלת התראת השיא מתוך מסך האימון (רק כשהאפליקציה ברקע) לא נבדקה; מסירה בפועל: NOT VERIFIED |
| P2-13.04 | 13 | כיבוד הרשאת POST_NOTIFICATIONS | PASS | Notifications.canPostNotifications | R: ServiceBehaviorTest.without_the_notification_permission_nothing_is_posted_and_nothing_crashes | ✔ |  |
| P2-13.05 | 13 | כיבוד Do Not Disturb | PASS | Notifications.ensureChannels (אין setBypassDnd) | R: ServiceBehaviorTest.the_notification_channels_respect_do_not_disturb (אף ערוץ לא עוקף DND) | ✔ | התנהגות DND בפועל במכשיר: NOT VERIFIED |
| P2-13.06 | 13 | תזמון אמין: Alarm עם setAndAllowWhileIdle, בניית מחדש בהפעלה ואחרי אתחול | PASS | ReminderScheduler, BootReceiver | R: ServiceBehaviorTest (alarm נוצר לעתיד, allowWhileIdle, בוטל); D: ReminderPlannerTest.reminders keep their wall clock time across the daylight saving change | ✔ |  |
| P2-13.07 | 13 | מסירת התראה/תזכורת בפועל במכשיר, כולל Doze וחיסכון בסוללה | BLOCKED | AlarmManager | X | — | NOT VERIFIED: אין מכשיר. התראות הן 'inexact' ועלולות להתאחר במצב סרק |
| P2-14.01 | 14 | גיבוי מלא: תוכניות, תרגילים מותאמים, אימונים, סטים, שיאים (נגזרים), מדדי גוף, יעדים, אירועים, הגדרות, תמונות | PASS | BackupCodec/BackupRepository (schema 2) | R: BackupRepositoryTest.export then restore into a fresh install gives identical data; BackupArchiveTest.a zip backup carries photos goals and custom fields; D: BackupV2Test | ✔ | שיאים אינם נשמרים כי הם נגזרים מהסטים - זה מכוון |
| P2-14.02 | 14 | פורמטים: JSON עם Schema ו-ZIP עם מדיה | PASS | BackupArchive | R: BackupArchiveTest(7); D: BackupTest | ✔ |  |
| P2-14.03 | 14 | ייצוא, בחירת מיקום שמירה, שיתוף, שחזור | PASS | BackupSection | R: BackupRepositoryTest/ShareIntentsTest; C: CreateDocument/OpenDocument | ✔ | בורר הקבצים: NOT VERIFIED (מערכת) |
| P2-14.04 | 14 | תצוגה מקדימה לפני ייבוא, אימות תקינות, זיהוי כפילויות | PASS | BackupValidator, preview.mergeNew | D: BackupTest (validation); R: BackupArchiveTest.merging the same archive again reports everything as already existing | ✔ |  |
| P2-14.05 | 14 | מניעת דריסה לא מכוונת + גיבוי נוסף לפני שחזור | PASS | safety copy ב-files/backups | R: BackupRepositoryTest.restoring over existing data replaces it and writes a safety copy first; merge adds only what is missing and is idempotent | ✔ |  |
| P2-14.06 | 14 | טיפול במיגרציות בין גרסאות גיבוי | PASS | BackupCodec (v1 נטען, v>נתמך נדחה) | D: BackupV2Test.a version 1 backup without the new tables still loads; BackupTest.a backup from a newer schema is refused | ✔ |  |
| P2-14.07 | 14 | קובץ גיבוי פגום מטופל בבטחה, ושמות קבצים מסוכנים בארכיון נחסמים | PASS | BackupValidator, ארכיון | D: BackupTest.a corrupt native backup fails cleanly; BackupV2Test.photo file names that could escape the photo folder are rejected; R: BackupArchiveTest.an archive without backup json or with garbage is refused; unsafe photo names inside an archive are ignored | ✔ |  |
| P2-14.08 | 14 | Import Adapter ל-JSON של Floot (version, plans, customExercises, sessions, measurements, events, active, name ...) | PARTIAL | FlootImporter.kt | D: FlootImporterTest(12) על מבנה שנבנה מתיאור המשתמש | ✔ | לא סופק קובץ ייצוא אמיתי מ-Floot - לא ניתן לקבוע תאימות מלאה (בהתאם לדרישת הפרומפט). NOT VERIFIED מול קובץ אמיתי |
| P2-15.01 | 15 | Health Connect | FUTURE | אין קוד | — | — | אין הכנה ייעודית; הארכיטקטורה (Repository + domain טהור) מאפשרת הוספה בלי שכתוב. נדרשות הרשאות ו-Health Connect SDK |
| P2-15.02 | 15 | Samsung Health / שעונים חכמים / צעדים / דופק / שינה | FUTURE | אין קוד | — | — | תלוי ב-Health Connect או SDK של Samsung; אין הכנה בקוד מלבד שדות sleepHours/ratings ידניים |
| P2-15.03 | 15 | ווידג'ט Android לאימון הבא | FUTURE | NextWorkoutResolver (לוגיקה מוכנה, טהורה) | D: NextWorkoutResolverTest | — | הלוגיקה ניתנת לשימוש חוזר; אין Glance/AppWidget |
| P2-15.04 | 15 | סנכרון Google Calendar / גיבוי לענן / מספר מכשירים / ייבוא מאפליקציות אחרות | FUTURE | BackupCodec + Importers + ActivityEvent | D: FlootImporterTest (מנגנון adapter קיים) | — | אין סנכרון; גיבוי ו-importer הם נקודות ההרחבה |
| P2-15.05 | 15 | האפליקציה עובדת אופליין ואינה תלויה בשירות חיצוני | PASS | אין INTERNET | R: ManifestPolicyTest | ✔ |  |
| P2-16.01 | 16 | מסך אימון: כפתורי +/- למשקל ולחזרות, מדרגות משקל לפי תרגיל | PASS | ExerciseCard (stepper), ExercisePrefs, equipment defaults | D: ProgressionEngineTest.increments follow the equipment and can be overridden; U: AppSmokeTest | ✔ | לחיצה על +/- ב-UI test לא בוצעה |
| P2-16.02 | 16 | סימון סט בלחיצה אחת; מעבר נוח לסט הבא; חיווי השלמת תרגיל; התקדמות כוללת | PASS | SetRow, ExerciseCard.allDone, WorkoutScreen progress | U: AppSmokeTest (סימון בלחיצה, פס מנוחה) | ✔ |  |
| P2-16.03 | 16 | נתוני אימון קודם ליד הנוכחיים; טיימר מנוחה זמין; שמירה אוטומטית; המשך אימון | PASS | ראה P1-5.* | R/U: ראה P1-5.03, P1-5.17, P1-5.22, P1-5.23 | ✔ |  |
| P2-16.04 | 16 | מסך אימון לא עמוס | PARTIAL | ExerciseCard | U: מבנה; C | — | NOT VERIFIED: שיפוט חזותי בלבד - ראה ביקורת איכות מוצר בדוח |
| P2-16.05 | 16 | הודעות שגיאה/הצלחה, מצבי טעינה ומצבים ריקים | PASS | Snackbars, LoadingBox, EmptyState | U: AppSmokeTest ('עוד אין נתוני אימונים', 'עוד אין מדידות', 'עוד אין אימונים ביומן'); ScreenSweepTest | ✔ |  |
| P2-16.06 | 16 | נגישות ויציבות לגודל גופן | PASS | contentDescription + Compose sp | U: ScreenSweepTest.home_and_a_new_workout_stay_usable_with_a_very_large_font (fontScale=2) | ✔ | TalkBack בפועל: NOT VERIFIED |
| P2-16.07 | 16 | אין כפתורים שאינם מחוברים לפעולה | PASS | הוחלפו 5 AssistChip(onClick={}) ב-InfoChip לא-לחיץ | B: חיפוש בקוד אחרי 'onClick = {}' - 0 תוצאות; C | ✔ | פער G3 תוקן בביקורת |
| P2-17.01 | 17 | מאות אימונים ואלפי סטים (ביצועי חישוב) | PASS | analytics חישוב בזיכרון | D: LargeHistoryTest.history of five years is analysed quickly; calendar of a month is built quickly from a long history | ✔ | ביצועי UI על מכשיר: NOT VERIFIED |
| P2-17.02 | 17 | שינוי תוכנית תוך כדי שימוש - אין השפעה על אימון פעיל | PASS | snapshot | R: WorkoutRepositoryTest.changing the plan afterwards does not alter an existing session | ✔ |  |
| P2-17.03 | 17 | אימון פעיל לאורך זמן; מעבר לרקע; נעילת מסך; שיחה נכנסת; סגירה לא צפויה; הפעלה מחדש | PARTIAL | WorkoutService, חותמות זמן מוחלטות, Room | R: an active workout survives the process being killed...; D: RestTimerTest.survives process death | ✔ / X | NOT VERIFIED: רקע/נעילה/שיחה/אתחול בפועל דורשים מכשיר |
| P2-17.04 | 17 | עבודה ללא אינטרנט | PASS | אין INTERNET | R: ManifestPolicyTest | ✔ |  |
| P2-17.05 | 17 | קובץ גיבוי פגום | PASS | ראה P2-14.07 | D/R | ✔ |  |
| P2-17.06 | 17 | מדידה עם ערך חריג | PASS | BodyAnalytics.outlier | D: BodyAnalyticsTest.unusual values trigger a gentle warning | ✔ |  |
| P2-17.07 | 17 | שינוי אזור זמן ושעון קיץ | PASS | תאריך מקומי + zoneId נשמרים בכל רשומה | D: TimeUtilsTest(6): local date uses the recorded zone; stable across the autumn daylight saving change; spring gap; R: MeasurementTest.late evening measurement keeps the local date of the zone it was taken in | ✔ |  |
| P2-17.08 | 17 | שינוי תאריך של אימון ישן | PASS | setTimes | R: WorkoutRepositoryTest.start and end time can be edited and the date follows | ✔ |  |
| P2-17.09 | 17 | עקביות נתונים וטרנזקציות; אין מסך שמציג מידע שונה ממסך אחר | PASS | נתונים נגזרים מ-Flow אחד (analyticsData, SharedFlow) | R: AnalyticsLiveUpdateTest.the live flow and the one-shot read agree after an edit; BackupRepositoryTest (rollback) | ✔ |  |
| P2-18.01 | 18 | אחסון מקומי פרטי; allowBackup כבוי; רכיבים לא חשופים | PASS | AndroidManifest.xml | R: ManifestPolicyTest.android auto backup is off and only the launcher activity of the app is exported | ✔ |  |
| P2-18.02 | 18 | הרשאות Android מינימליות | PASS | רק POST_NOTIFICATIONS, FOREGROUND_SERVICE(+SPECIAL_USE), VIBRATE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED | R: ManifestPolicyTest.the app asks only for the permissions the timer needs... | ✔ | הרשאה פנימית אחת של AndroidX (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) מוצהרת במפורש בבדיקה |
| P2-18.03 | 18 | הגנה על תמונות התקדמות; אי-העברה לשירותים חיצוניים ללא אישור | PASS | files/photos פרטי; FileProvider לשיתוף בלבד | R: ManifestPolicyTest; U: PhotoPipelineTest | ✔ |  |
| P2-18.04 | 18 | שליטה מלאה בייצוא; מניעת חשיפת מפתחות API | PASS | ReportOptions; אין מפתחות בקוד | R: ManifestPolicyTest.the source has no network code; D: AiProviderTest | ✔ |  |
| P2-18.05 | 18 | אפשרות למחוק מידע מהאפליקציה | PASS | BackupRepository.deleteAll + דיאלוג עם הקלדת אישור | R: BackupArchiveTest.delete everything removes personal data and photo files, then restores the starting state; U: DeleteConfirmationTest (כלל האישור) | ✔ | זרימת ההגדרות המלאה (לחיצה על 'מחק את כל הנתונים שלי' -> דיאלוג): NOT VERIFIED ב-UI (שדה טקסט בדיאלוג תוקע את Robolectric) |
| P2-19.01 | 19 | הרחבת הבדיקות לכל הנושאים ברשימה וביצוע בפועל | PASS | domain/src/test, app/src/test | ראה P2-2.* עד P2-18.* | ✔ |  |
| P2-19.02 | 19 | בדיקת ייצוא PDF | BLOCKED | PdfExport.kt | D: ReportBlocksTest (חלוקה לעמודים בלבד); U: PdfExportAttemptTest דולג | — | NOT VERIFIED: ראה P2-11.05 |
| P2-19.03 | 19 | בדיקות UI מתאימות | PASS | app/src/test/.../ui | U: AppSmokeTest(5), AppFlowsTest(5), ScreenSweepTest, PhotoPipelineTest | ✔ | Robolectric בלבד; בדיקות instrumentation על מכשיר: BLOCKED |
| P2-20.01 | 20 | סקירת קוד, אין שגיאות קומפילציה, הסרת קוד שאינו בשימוש | PASS | קוד שהוסר ב-11c52ce (DatedLineChart, CalendarBuilder.forDay...) | B: קומפילציה ירוקה; C | ✔ |  |
| P2-20.02 | 20 | בדיקות רגרסיה, מסכי האפליקציה, שמירה, ייבוא/ייצוא | PASS | CI | B + U + R | ✔ |  |
| P2-20.03 | 20 | APK מעודכן + הוראות התקנה ל-S26 Ultra | PASS | README.md ('התקנה') | B: ארטיפקטים; C: הוראות | ✔ | התקנה בפועל: NOT VERIFIED |
| P2-20.04 | 20 | Release חתום - הסבר מה נדרש | PARTIAL | README.md + app/build.gradle.kts | B | ✔ | ה-release שנבנה חתום במפתח ה-debug המשותף (לא להפצה) |
| P2-20.05 | 20 | לא לטעון בדיקה פיזית שלא בוצעה | PASS | כל המסמכים | C | ✔ | מצוין במפורש בכל מקום |
| P2-21.01 | 21 | עדכון README, CLAUDE, ARCHITECTURE, DATA_MODEL, FEATURES, DEVELOPMENT_PLAN, CHANGELOG, TESTING | PASS | קבצי השורש | C | ✔ |  |
| P2-22.01 | 22 | בצע בפועל; אל תמחק יכולות; אל תציג כפתורים לא מחוברים או נתונים פיקטיביים | PASS | — | ראה P2-16.07, P2-2.18 | ✔ |  |
| P2-22.02 | 22 | כשיש תלות בהרשאות/שירות חיצוני - להסביר ולהמשיך ברכיבים העצמאיים | PASS | FEATURES.md | C | ✔ |  |
| P2-23.01 | 23 | היכולת לענות: האם אני מתחזק? איפה אני תקוע? כמה סטים לשריר? תדירות? שינוי במשקל והיקפים? נפח מול מטרות? מה לעשות באימון הבא? | PASS | Dashboard, InsightEngine, VolumeAnalytics, BodyAnalytics, ProgressionEngine | D: DashboardTest, InsightEngineTest, VolumeAnalyticsTest, BodyAnalyticsTest, ProgressionEngineTest | ✔ | 'נפח מתאים למטרות' נענה חלקית: אין יעד נפח מפורש (יעדי תדירות כן) |

## 5. מיפוי רשימות הבדיקה של הביקורת

| סעיף ביקורת | שורות במטריצה |
|---|---|
| 3.1 ארכיטקטורה, Room, אופליין, קומפילציה, APK, הוראות, שגיאות, מיגרציות | P1-1.01, P1-1.02, P1-1.03, P1-1.04, P1-1.05, P1-1.06, P1-1.07, P1-9.01, P1-9.02, P1-9.03, P1-12.01, P1-12.02, P1-12.03, P1-12.04, P1-12.05, P1-13.01, P1-13.02 |
| 3.2 עברית, RTL, כהה, אייקונים, ניווט תחתון, מקלדת מספרית, כפתורים גדולים, הודעות, טעינה, מצבים ריקים, יציבות גודל טקסט | P1-2.01, P1-2.02, P1-2.03, P1-2.04, P1-2.05, P1-2.06, P1-2.07, P1-2.08, P1-2.09, P1-2.10, P1-2.11, P1-2.12, P1-2.13, P2-16.05, P2-16.06 |
| 3.3 מסך הבית | P1-3.01, P1-3.02, P1-3.03, P1-3.04, P1-3.05, P1-3.06, P1-3.07, P1-3.08, P1-3.09 |
| 3.4 תוכניות (כולל ארבעת האימונים המדויקים והיסטוריה שלא משתנה) | P1-4.01, P1-4.02, P1-4.03, P1-4.04, P1-4.05, P1-4.06, P1-4.07, P1-4.08, P1-4.09, P1-4.10, P1-4.11, P1-4.12, P1-4.13 |
| 3.5 אימון פעיל (טיימר מנוחה, התראות לפי הרשאות, שמירה אוטומטית, חידוש) | P1-5.01, P1-5.02, P1-5.03, P1-5.04, P1-5.05, P1-5.06, P1-5.07, P1-5.08, P1-5.09, P1-5.10, P1-5.11, P1-5.12, P1-5.13, P1-5.14, P1-5.15, P1-5.16, P1-5.17, P1-5.18, P1-5.19, P1-5.20, P1-5.21, P1-5.22, P1-5.23, P1-5.24 |
| 3.6 ספריית תרגילים (100+ עם כל השדות) | P1-6.01, P1-6.02, P1-6.03, P1-6.04, P1-6.05 |
| 3.7 יומן, היסטוריה, עריכת עבר, הזנת עבר, מדדי גוף | P1-7.01, P1-7.02, P1-7.03, P1-7.04, P1-8.01, P1-8.02, P1-8.03, P1-8.04 |
| 3.8 גיבוי, ייצוא, ייבוא, CSV, אימון בודד, העתקה, Share Sheet, אימות, אישור דריסה, אין אובדן נתונים | P1-10.01, P1-10.02, P1-10.03, P1-10.04, P1-10.05, P1-10.06, P1-10.07, P1-10.08, P1-10.09, P1-10.10, P1-10.11, P1-10.12, P1-10.13 |
| 4.1 אנליטיקה | P2-2.01, P2-2.02, P2-2.03, P2-2.04, P2-2.05, P2-2.06, P2-2.07, P2-2.08, P2-2.09, P2-2.10, P2-2.11, P2-2.12, P2-2.13, P2-2.14, P2-2.15, P2-2.16, P2-2.17, P2-2.18 |
| 4.2 גרפים (נקודות אינטראקטיביות, מסננים, עדכון אחרי עריכה) | P2-3.01, P2-3.02, P2-3.03, P2-3.04, P2-3.05, P2-3.06, P2-3.07, P2-3.08, P2-3.09, P2-3.10 |
| 4.3 Progressive Overload | P2-4.01, P2-4.02, P2-4.03, P2-4.04, P2-4.05, P2-4.06, P2-4.07, P2-4.08, P2-4.09 |
| 4.4 שיאים אישיים | P2-5.01, P2-5.02, P2-5.03, P2-5.04, P2-5.05, P2-5.06 |
| 4.5 יומן ולוח שנה | P2-6.01, P2-6.02, P2-6.03, P2-6.04, P2-6.05, P2-6.06, P2-6.07, P2-6.08, P2-6.09 |
| 4.6 מדדי גוף ותמונות | P2-7.01, P2-7.02, P2-7.03, P2-7.04, P2-7.05, P2-7.06, P2-7.07, P2-7.08, P2-8.01, P2-8.02, P2-8.03, P2-8.04, P2-8.05, P2-8.06 |
| 4.7 יעדים והתאוששות | P2-9.01, P2-9.02, P2-9.03, P2-9.04, P2-9.05, P2-10.01, P2-10.02, P2-10.03, P2-10.04 |
| 4.8 AI וייצוא (PDF, TXT, JSON, CSV, העתקה, שיתוף, תובנות מקומיות מופרדות, בלי העברה אוטומטית) | P2-11.01, P2-11.02, P2-11.03, P2-11.04, P2-11.05, P2-11.06, P2-11.07, P2-11.08, P2-11.09, P2-11.10, P2-11.11, P2-11.12, P2-11.13, P2-11.14, P2-11.15, P2-12.01, P2-12.02, P2-12.03 |
| 4.9 התראות וגיבויים מתקדמים (כולל Floot 'אם סופק קובץ') | P2-13.01, P2-13.02, P2-13.03, P2-13.04, P2-13.05, P2-13.06, P2-13.07, P2-14.01, P2-14.02, P2-14.03, P2-14.04, P2-14.05, P2-14.06, P2-14.07, P2-14.08 |
| 4.10 אינטגרציות עתידיות (FUTURE + האם יש הכנה אדריכלית) | P2-15.01, P2-15.02, P2-15.03, P2-15.04, P2-15.05 |

### 5.1 אינטגרציות עתידיות (4.10) - האם יש הכנה אדריכלית?

| אינטגרציה | סטטוס | הכנה אדריכלית קיימת? |
|---|---|---|
| OpenAI / Anthropic API | FUTURE | **כן** - ממשק `AiProvider` עם בקשה שאינה נבנית בלי הסכמה מתועדת; אין מפתחות, אין הרשאת INTERNET |
| Health Connect / Samsung Health / שעונים / צעדים / דופק / שינה | FUTURE | **חלקית** - מודל Repository + domain טהור מאפשר מקור נתונים נוסף; אין קוד ייעודי ואין הרשאות |
| ווידג'ט האימון הבא | FUTURE | **חלקית** - `NextWorkoutResolver` הוא לוגיקה טהורה ונבדקת; אין AppWidget/Glance |
| Google Calendar / גיבוי לענן / מספר מכשירים | FUTURE | **חלקית** - `BackupCodec` עם schemaVersion ו-importers; אין סנכרון, זהויות או אימות |
| ייבוא מאפליקציות אחרות | FUTURE | **כן** - מנגנון adapter (`FlootImporter`) כדוגמה |

## 6. תרחישי קצה-לקצה A-E

### תרחיש A - אימון בחדר הכושר

הוכחה: ScenarioATest.a_full_workout_from_opening_the_app_to_the_next_session (UI, Robolectric)

| שלב | פעולה | תוצאה | פירוט |
|---|---|---|---|
| 1 | פתיחת LiftOS | PASS | ממתין למסך הבית ('התחל אימון') |
| 2 | בחירת Upper A | PASS | בית -> צ'יפ התוכנית -> Upper A -> 'התחל אימון זה' |
| 3 | התחלת אימון | PASS | מסך האימון נפתח ('סיים'); אימון פעיל נוצר במסד |
| 4 | צפייה בנתוני האימון הקודם | PASS | מוצג 'קודם (תאריך): 40×8' (אימון Upper A קודם נזרע במסד) |
| 5 | הזנת משקל | PASS | 60 נכתב לשדה והגיע למסד (נבדק) |
| 6 | הזנת חזרות | PASS | 8 -> מסד |
| 7 | הזנת RIR | PASS | 2 -> מסד |
| 8 | סימון סט כהושלם | PASS | לחיצה על 'סמן כבוצע'; הסט מסומן במסד |
| 9 | הפעלת טיימר מנוחה | PASS | מופיע 'דלג על המנוחה'; session.rest במסד |
| 10 | מעבר לאפליקציה אחרת | PARTIAL | בוצע כמעבר מחזור-חיים (CREATED); מעבר אמיתי לאפליקציה אחרת לא נבדק - NOT VERIFIED |
| 11 | חזרה ל-LiftOS | PARTIAL | חזרה ל-RESUMED: המסך, פס המנוחה והנתונים נשמרו; NOT VERIFIED על מכשיר |
| 12 | הוספת סט | PASS | לחיצה על 'הוסף סט': מספר הסטים בתרגיל גדל ב-1 (מסד) |
| 13 | שינוי תרגיל | PARTIAL | ההחלפה בוצעה דרך ה-Repository והמסך עודכן לשם החדש; בורר התרגילים (Bottom sheet עם שדה חיפוש) לא נבדק ב-UI |
| 14 | השלמת האימון | PASS | 'סיים' -> 'לסיים את האימון?' -> 'סיים ושמור' |
| 15 | הוספת הערות | PARTIAL | הערת האימון נכתבה דרך ה-Repository והופיעה בסיכום ובדוח; שדה 'הערות כלליות לאימון' (שמירה מושהית) לא הוקלד ב-UI |
| 16 | שמירת האימון | PASS | סטטוס COMPLETED במסד; אין אימון פעיל; מסך הסיכום נפתח |
| 17 | בדיקת הופעתו ביומן | PASS | יומן -> היסטוריה: לפחות שתי שורות 'Upper A' (הקודם והחדש) |
| 18 | בדיקת עדכון האנליטיקה | PASS | analyticsData מכיל 2 אימונים; לשונית התקדמות מציגה תוכן (לא מצב ריק) |
| 19 | העתקת דוח ל-ChatGPT | PASS | בוצע במסך הסיכום (לפני מעבר ליומן): הלוח מכיל הערות, 'האימון הקודם' וההוראה בסוף |
| 20 | אימון מאותו סוג בפעם הבאה ואימות נתוני הפתיחה | PASS | Upper A שוב: 'קודם (...): 60×8'; הסט הראשון נטען 60×8 ולא מסומן כבוצע |

סיכום: PASS 16, PARTIAL 4

### תרחיש B - מעקב התקדמות

הוכחה: AnalyticsLiveUpdateTest (נתונים) + ScreenSweepTest (UI)

| שלב | פעולה | תוצאה | פירוט |
|---|---|---|---|
| 1 | הזנת כמה אימונים היסטוריים | PASS | createPastWorkout x3-5 (תאריכים שונים) ב-AnalyticsLiveUpdateTest וב-Sweep |
| 2 | שינוי משקלים בין אימונים | PASS | 50 -> 55 -> 60 ק"ג (AnalyticsLiveUpdateTest); 40 -> 50 (Sweep) |
| 3 | פתיחת גרף התקדמות | PASS | לשונית התקדמות: 6 בחירות גרף נבחרות (every_chart_choice_can_be_selected); גרף משקל גוף מוצג |
| 4 | זיהוי שיא אישי | PASS | AnalyticsLiveUpdateTest.topWeightEvent; מסך 'שיאים אישיים' מציג 'שיאים אחרונים' (Sweep) |
| 5 | קבלת המלצת Progressive Overload | PASS | עמוד התרגיל מציג 'הצעה לאימון הבא' (Sweep); ProgressionEngineTest(19) |
| 6 | עריכת אימון קודם | PASS | setWeight על סט היסטורי (AnalyticsLiveUpdateTest); מסך העריכה נפתח מהסיכום (Sweep) |
| 7 | אימות חישוב מחודש של האנליטיקה | PASS | נפח, משקל מקסימלי ואירוע השיא השתנו מיד (AnalyticsLiveUpdateTest); ה-flow והקריאה החד-פעמית מסכימים |

סיכום: PASS 7

### תרחיש C - מדדי גוף

הוכחה: MeasurementTest, BodyAnalyticsTest, AppFlowsTest, ScreenSweepTest, PhotoPipelineTest

| שלב | פעולה | תוצאה | פירוט |
|---|---|---|---|
| 1 | הזנת משקל | PASS | AppFlowsTest.a_body_measurement_can_be_added_from_the_body_tab; MeasurementTest |
| 2 | הזנת היקפים | PASS | MeasurementTest.all metrics are stored... |
| 3 | הזנת מדידה מתאריך קודם | PASS | MeasurementRepository.add(measuredAt) - Sweep מזריע מדידות מלפני 20 ו-2 ימים; MeasurementTest.late evening measurement keeps the local date |
| 4 | הצגת גרף | PASS | לשונית 'גוף' מציגה גרף משקל גוף (desc='גרף משקל גוף') עם סטטיסטיקה (Sweep) |
| 5 | עריכת מדידה | PASS | MeasurementTest.a measurement can be edited and its date moved (נתונים); לחיצת העריכה ב-UI לא נבדקה |
| 6 | אימות עדכון הגרף | PARTIAL | הגרף נגזר מ-observeAll; HomeDataTest מוכיח שהמשקל האחרון עוקב אחרי עריכה/מחיקה; אין בדיקת UI שמשווה גרף לפני/אחרי |
| 7 | הוספת תמונה | PARTIAL | צינור הקליטה נבדק במלואו (PhotoPipelineTest: הקטנה, EXIF, פרטיות); המצלמה והגלריה עצמן NOT VERIFIED |
| 8 | השוואה בין שתי תמונות | PASS | two_progress_photos_can_be_picked_and_compared: בחירת שתי תמונות -> 'השווה' -> מסך 'השוואת תמונות'; מחוות ה-Slider NOT VERIFIED |

סיכום: PASS 6, PARTIAL 2

### תרחיש D - יומן ואירועים

הוכחה: CalendarTest(17), CalendarRepositoryTest(6), AppFlowsTest, ScreenSweepTest

| שלב | פעולה | תוצאה | פירוט |
|---|---|---|---|
| 1 | יצירת אימון מתוכנן | PASS | תוכנית פעילה עם ימים -> CalendarBuilder מציג 'מתוכנן' (CalendarTest.future workouts of the plan appear as planned); אירוע STRENGTH PLANNED נשמר (CalendarRepositoryTest) |
| 2 | שינוי התאריך | PASS | moveWorkout/moveOccurrence (CalendarTest.moving a planned workout shows it on the new day only; CalendarRepositoryTest.moving one occurrence keeps the time of day) |
| 3 | יצירת אירוע חוזר | PASS | Recurrence (CalendarTest.weekly rule..., every other week...); UI: AppFlowsTest.an_event_added_in_the_journal_is_saved_and_shown (אירוע יחיד) |
| 4 | שינוי מופע יחיד | PASS | CalendarRepositoryTest.marking one occurrence of a repeating event leaves the rest of the series alone; deleting one occurrence only hides that date |
| 5 | השלמת אימון | PASS | CalendarRepositoryTest.linking an occurrence to a workout marks it done and can be undone; CalendarTest.a recorded workout replaces the planned entry of that day |
| 6 | אימות שינוי הסטטוס ביומן | PASS | CalendarTest.skipped and missed marks are explicit and never automatic; markers summarise each day |
| 7 | מעבר בין תצוגות חודש, שבוע ויום | PASS | ScreenSweepTest.all_five_journal_views_open_and_the_history_lists_the_workouts (חודש/שבוע/יום/רשימה/היסטוריה) |

סיכום: PASS 7

### תרחיש E - שחזור וגיבוי

הוכחה: ScenarioETest + BackupRepositoryTest + BackupArchiveTest (מסדי נתונים נפרדים בזיכרון)

| שלב | פעולה | תוצאה | פירוט |
|---|---|---|---|
| 1 | יצירת תוכנית חדשה | PASS | ScenarioETest: תוכנית 'Push Pull' עם אימון ושני תרגילים |
| 2 | רישום אימון | PASS | אימון מהתוכנית החדשה, 4+ סטים, הערה |
| 3 | הזנת מדדי גוף | PASS | מדידה עם משקל, מותניים וחזה |
| 4 | יצירת גיבוי | PASS | exportJson - קובץ לא ריק |
| 5 | אימות תקינות הקובץ | PASS | preview(): canImport=true וסיכום תקין (1 אימון, 1 מדידה, 2 תוכניות) |
| 6 | שחזור בסביבת בדיקה מבודדת | PASS | מסד נתונים שני בזיכרון, REPLACE_ALL |
| 7 | אימות שכל הנתונים שוחזרו | PASS | שוויון מלא של plans, plannedWorkouts, plannedExercises, sessions, sessionExercises, sets, measurements |
| 8 | בדיקת קשרים בין תרגילים, סטים ואימונים | PASS | כל sessionExercise מצביע על אימון קיים וכל סט על תרגיל קיים; getDetail מחזיר את העץ המלא |
| 9 | בדיקה שאין כפילויות | PASS | preview חוזר: mergeNew=0; MERGE שני לא משנה אף מונה |
| 10 | בדיקה שאין אובדן נתונים | PASS | המסד המקורי זהה לאחר הגיבוי; BackupRepositoryTest.restoring over existing data ... writes a safety copy first |

סיכום: PASS 10

## 7. בדיקות איכות Android

| בדיקה | סטטוס | הערה / מה נדרש |
|---|---|---|
| קומפילציית Debug | PASS | CI: assembleDebug ירוק; APK נוצר (ראה FINAL_ACCEPTANCE_REPORT.md) |
| קומפילציית Release (R8) | PASS | CI: assembleRelease ירוק; APK 2.66MB. התנהגות בזמן ריצה אחרי R8: NOT VERIFIED |
| Unit tests (domain) | PASS | JVM, CI |
| Unit tests (app, Robolectric non-UI) | PASS | Room, repositories, שירותים, התראות, מניפסט |
| Instrumentation tests | BLOCKED | אין מכשיר/אמולטור בסביבה (גם ב-CI לא מוגדר). נדרש: Android Emulator או מכשיר + androidTest |
| Compose UI tests | PASS | 40 בדיקות ב-Robolectric (JVM): 39 עברו, 1 דולגה (PDF). לא מכשיר אמיתי; ראה TESTING.md |
| Room migrations | PASS | MigrationTest (מסד v1 אמיתי -> v2), DurabilityTest (מיגרציה לכל צעד, סכימה מיוצאת, מסד חדש מדי נכשל בבטחה) |
| ייבוא וייצוא | PASS | BackupRepositoryTest, BackupArchiveTest, ScenarioETest, ShareIntentsTest; ציור PDF: BLOCKED (PdfExportAttemptTest דולג) |
| התנהגות ללא אינטרנט | PASS | ManifestPolicyTest: אין INTERNET ואין קוד רשת |
| שמירה לאחר סגירה לא צפויה | PASS | WorkoutRepositoryTest.an active workout survives the process being killed..., DurabilityTest; הרג תהליך אמיתי: NOT VERIFIED |
| הרשאות | PASS | ManifestPolicyTest; ServiceBehaviorTest.without_the_notification_permission...; בקשת ההרשאה בזמן ריצה: NOT VERIFIED |
| עבודה ברקע | PARTIAL | שירות חזית + Alarm: מנגנונים נבדקו ב-Robolectric; Doze/חיסכון בסוללה של Samsung: BLOCKED |
| טיימרים והתראות | PARTIAL | לוגיקה ותזמון נבדקו (ServiceBehaviorTest, RestTimerTest, ReminderPlannerTest); מסירה בפועל: BLOCKED |
| RTL | PASS | ScreenSweepTest.the_whole_app_is_laid_out_right_to_left; הצגה חזותית לא נבדקה |
| ביצועים | PARTIAL | LargeHistoryTest (חמש שנות היסטוריה מחושבות מהר) ב-JVM; זמני מסך/גלילה/אתחול על מכשיר: NOT VERIFIED |
| זיכרון בסיסי | PARTIAL | תמונות מוקטנות ל-2048px ונטענות עם inSampleSize (PhotoPipelineTest); פרופיילינג זיכרון: BLOCKED |
| יציבות מסכים | PASS | ScreenSweepTest פותח את כל המסכים המרכזיים עם נתונים; גופן x2.0 לא שובר את הבית ואת מסך האימון |

## 8. פערים שנמצאו, חומרה וטיפול

| מזהה | חומרה | הפער | גורם | תיקון | בדיקה ממוקדת | מצב |
|---|---|---|---|---|---|---|
| G-AI-REPORT | High | דוח ה-AI של אימון בודד לא כלל את השיאים שנקבעו באימון, נתוני מגמות ודירוגי התאוששות (פרומפט 2 §11) | שירת הדוח נבנה בלי הקשר היסטורי | WorkoutReportContext + TextReport.aiReport/appendHeader; חיבור ב-AiExportViewModel וב-SummaryViewModel | D: WorkoutReportExtrasTest(6); U: ScreenSweepTest.the_quick_copy_report_of_a_workout... | סגור |
| G4 | High | כפתור 'העתק דוח לניתוח ב-ChatGPT' (פרומפט 1 §10) הוסר ממסך הסיכום בחלק 2 | הוחלף בכפתור 'נתח את האימון עם AI' בלבד | הוחזר כפתור העתקה מהירה שמשתמש באותו דוח | U: AppFlowsTest (תוכן הלוח), ScenarioATest.A19 | סגור |
| G3 | Medium | חמישה צ'יפים שנראו ככפתורים ולא עשו דבר (AssistChip עם onClick ריק) - הפרת 'כל כפתור חייב לעבוד' | שימוש ב-AssistChip כתווית | InfoChip לא-לחיץ במקומם | B: אין עוד 'onClick = {}' בקוד; U: Sweep | סגור |
| G1 | Medium | 'משקל ממוצע לסטי עבודה' חושב אבל לא הוצג (פרומפט 2 §2) | שדה ב-domain בלי UI | שורה בכל אימון בהיסטוריית התרגיל | U: ScreenSweepTest.the_exercise_screen_shows_average_working_weight... | סגור |
| G2 | Medium | נפח לכל סט לא הוצג (פרומפט 2 §2) | הוצג רק נפח כולל | 'נפח לכל סט' בהיסטוריית התרגיל | U: ScreenSweepTest (אותו מבחן) | סגור |
| G6 | Low | פתיחת ChatGPT/Claude: העתקה + פתיחת האפליקציה בלבד | — | שיתוף ישיר לחבילה (ACTION_SEND) עם נפילה מדורגת והודעות כנות | R: ShareIntentsTest | סגור (קבלת הטקסט באפליקציה היעד: NOT VERIFIED) |
| G5 | Low | אנימציות מינימליות | — | דהייה בסימון סט | C | סגור |
| G7 | Low | מסך הבית ללא תובנות מקומיות (הן במסך ההתקדמות) | החלטת עיצוב | לא שונה - נשאר ב-AnalyticsScreen | — | פתוח (שיפור עתידי, אינו דרישה) |
| T-MANIFEST | Low | הבדיקה הראשונה של המניפסט נכשלה: המניפסט הממוזג מכיל הרשאה פנימית של AndroidX (DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) | הרשאת signature שנוספת אוטומטית על ידי androidx.core | הבדיקה מצהירה עליה במפורש ובודקת שאין אחרות | R: ManifestPolicyTest | סגור |
| T-SWEEP | Low | שלוש בדיקות UI נכשלו בריצה הראשונה בגלל הבדיקות עצמן: כיוון הפריסה נקרא מחוץ לערכת הנושא; שורה מתחת לקפל לא נלחצה; חלקי מסך 'גוף' ברשימה עצלה | טעות בבדיקה | קריאה מתוך ה-theme, גלילה לפני לחיצה, scrollToNode | U: ScreenSweepTest | סגור |
| OPEN-PDF | High | ייצוא PDF: ציור המסמך בפועל (PdfDocument) לא הורץ באף בדיקה | PdfDocument אינו שמיש ב-Robolectric (נוסה גם בגרפיקה native: הבדיקה דולגה) | חלוקת העמודים נבדקת ב-domain; הציור לא | D: ReportBlocksTest (חלקי); U: PdfExportAttemptTest (דולג) | פתוח - BLOCKED, דורש מכשיר/אמולטור |
| OPEN-DEVICE | High | אין שום בדיקה על מכשיר או אמולטור: טיימר ברקע/נעילה, התראות, מצלמה/גלריה, בורר קבצים, Share Sheet, התקנה | אין מכשיר בסביבה | הוראות בדיקה ידנית ב-TESTING.md | — | פתוח - BLOCKED |
| OPEN-SIGN | Medium | ה-APK ה-release חתום במפתח ה-debug המשותף ולא במפתח הפצה | אין keystore.properties בסביבה | הסבר ב-README | — | פתוח - דורש מפתח חתימה של הבעלים |
| OPEN-FLOOT | Medium | Importer של Floot נבדק רק מול מבנה שנבנה מתיאור המשתמש; לא סופק קובץ אמיתי | אין קובץ ייצוא אמיתי | — | D: FlootImporterTest | פתוח - ממתין לקובץ |
| OPEN-DRAG | Medium | גרירה לשינוי סדר (תוכנית) ו-Slider להשוואת תמונות לא נבדקו כמחווה | Robolectric אינו מדמה גרירה מורכבת | הלוגיקה נבדקת | R: reorder tests | פתוח - BLOCKED |
| OPEN-SHEET | Low | בורר התרגילים (החלפה/הוספה) ושדות טקסט בתוך דיאלוגים/Bottom sheets לא נבדקים ב-UI | שדה טקסט בחלון נפרד אינו נרגע ב-Robolectric | הלוגיקה נבדקת ב-Repository | R: WorkoutRepositoryTest | פתוח - הגבלת כלי בדיקה |
| G-NOTIF | Medium | התראות יעד/שיא אישי (goalReached, newRecord) ללא בדיקה שמפרסמת אותן | אף בדיקה לא הפעילה אותן | נוספו בדיקות: יעד שהושג מפרסם התראה אחת ומסומן, מתג נפרד, התראת שיא בערוץ ההישגים | R: ServiceBehaviorTest (3 בדיקות) | סגור (מסירה בפועל במכשיר: NOT VERIFIED) |
| OPEN-MASTER | Low | פרומפט המאסטר הארוך נחתך ב-20,000 תווים בתמלול; רק פרומפט 1 ופרומפט 2 נבדקו במלואם | מגבלת קלט | הסעיפים שנקראו אומתו מול פרומפטים 1 ו-2 (חופפים) | — | הוצהר - לא נבדק מעבר לפרומפטים 1 ו-2 |

**אין פער Critical פתוח.** לא נמצאו אובדן נתונים, קריסות או אימון שלא נשמר בבדיקות שהורצו. פערי High פתוחים (`OPEN-PDF`, `OPEN-DEVICE`) חסומים בגלל הסביבה, לא בגלל קוד שאפשר לתקן כאן.

## 9. ביקורת איכות מוצר (כמשתמש אמיתי)

| שאלה | תשובה מבוססת-ראיות |
|---|---|
| האם נוח להזין סט בזמן אימון? | כן, ברמת הקוד והבדיקות: שדות מספריים, +/- לפי צעד הציוד, סימון בלחיצה אחת, ערכים מוטענים מהאימון הקודם. מה שלא נבדק: הקלדה בפועל באגודל על S26 Ultra. |
| האם הפעולות החשובות זמינות במהירות? | 'סיים', 'הוסף סט', פס מנוחה עם +15/+30/דילוג ובאנר 'המשך אימון' זמינים מכל מסך. |
| האם מסך האימון עמוס מדי? | NOT VERIFIED חזותית. מבנית: כל תרגיל בכרטיס; נתוני ההתאוששות האופציונליים (קושי, איכות, שינה) במקטע שנפתח לפי בקשה; ההצעה לאימון הבא בכרטיס נפרד. לא בוצע שינוי עיצובי. |
| האם קל למצוא תרגיל? | כן: חיפוש בעברית ובאנגלית (בלי ניקוד), סינון לפי שריר וציוד (ExerciseSearchTest, AppSmokeTest). |
| האם ברור מהו המשקל הקודם? | כן: שורה 'קודם (dd/MM): 40×8 · ...' מעל הסטים, ורמז בכל שדה ריק (ScenarioATest.A4). |
| האם קל לערוך תוכנית? | בדרך כלל כן (עורך תרגיל, שכפול, תבניות). גרירה לשינוי סדר לא נבדקה כמחווה. |
| האם קל לתקן אימון היסטורי? | כן: סיכום -> 'ערוך אימון' פותח את אותו מסך אימון במצב עריכה (ScreenSweepTest). הנתונים מחושבים מחדש מיד. |
| האם הגרפים מובנים? | הנתונים והטקסטים נבדקו (כותרת, יחידות, הודעת 'אין מספיק נתונים'). קריאות הציור על מסך אמיתי: NOT VERIFIED. |
| האם כל הכפתורים עובדים? | נמצאו וטופלו 5 צ'יפים דקורטיביים שנראו כלחיצים (G3); אין עוד onClick ריק בקוד. לא נלחץ כל כפתור אחד-אחד ב-UI. |
| האם התראות המנוחה מתנהגות כראוי? | הלוגיקה נבדקה (פעם אחת, לפי הגדרה והרשאה). התנהגות במכשיר: NOT VERIFIED. |
| האם האפליקציה ברורה בעברית? | הטקסטים בעברית מלאה עם מספרים ב-LTR בתוך משפטים; הניסוח לא עבר סקירת דובר שפה על מסך אמיתי. |
| האם משתמש יכול להבין איך להשתמש בה ללא הדרכה? | מצבים ריקים מסבירים מה לעשות ('עוד אין נתוני אימונים...'); אין מסך הדרכה ראשוני. שיפור אפשרי: Low. |
| האם אפשר להשתמש בה חודשים בלי לחשוש לאובדן נתונים? | בסיס חזק: מיגרציות תוספתיות בלבד, טרנזקציות, גיבוי JSON/ZIP עם אימות, גיבוי בטיחות לפני שחזור, תזכורת גיבוי. הסיכון שנותר: הנתונים חיים רק במכשיר עד שהמשתמש מייצא גיבוי (allowBackup כבוי במכוון). |

לא בוצע עיצוב מחדש. שינויי ה-UX שנעשו: כפתור ההעתקה המהיר בסיכום, ממוצע משקל ונפח-לסט בהיסטוריית התרגיל, צ'יפים לא-לחיצים ודהיית שורת סט.
