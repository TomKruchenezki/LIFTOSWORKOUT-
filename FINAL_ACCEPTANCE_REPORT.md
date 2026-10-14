# FINAL_ACCEPTANCE_REPORT - דוח קבלה סופי של LiftOS

> גרסה `0.2.1 (versionCode 3)` · Commit הקוד הסופי `e9aa61f773ea9c476d1f121d4221da70ebf6a2c3` · ריצת CI `38565145090` · תוצאה: **success (כל השלבים, כולל Test gate)**
> כל הטענות כאן נתמכות בבדיקות שרצו ב-GitHub Actions. **שום דבר לא נבדק על מכשיר פיזי או אמולטור.** הפירוט המלא ברמת שורה: [FINAL_REQUIREMENTS_AUDIT.md](FINAL_REQUIREMENTS_AUDIT.md).

## המלצה סופית

# **READY WITH LIMITATIONS**

**מה זה אומר:** אפשר וכדאי להתקין את ה-Debug APK על ה-Galaxy S26 Ultra ולהתחיל להשתמש בו לניסיון. לא נמצאה ולו בעיה קריטית ידועה (אובדן נתונים, קריסה, אימון שלא נשמר, APK שלא נבנה).

**למה לא `READY FOR DEVICE TESTING` בלי הסתייגות:** יש מגבלות משמעותיות שאני לא מוכן להסתיר:

* **אפס בדיקות על מכשיר.** הטיימר ברקע ובנעילה, התראות, מצלמה וגלריה, בורר הקבצים, חלון השיתוף וחיסכון הסוללה של Samsung נבדקו רק ברמת הלוגיקה והמנגנון ב-Robolectric.
* **ציור ה-PDF** (ניסיון לצייר PDF אמיתי תחת Robolectric בגרפיקה native (`PdfExportAttemptTest`) **דולג** - `PdfDocument` אינו שמיש בסביבה הזו, ולכן ציור ה-PDF לא נבדק (נבדקת רק חלוקת העמודים ב-domain). בדיקה שדולגה אינה ראיה).
* **ה-Release APK אינו גרסת הפצה:** הוא חתום במפתח ה-debug המשותף (אין `keystore.properties`). לשימוש אישי זה בסדר; להפצה נדרש מפתח משלך.
* **ייבוא Floot** נבדק רק מול מבנה שנבנה מתיאור, לא מול קובץ אמיתי.
* גרירה לשינוי סדר, ה-Slider להשוואת תמונות, בורר התרגילים (Bottom sheet עם חיפוש) ומראה הגרפים על המסך האמיתי לא נבדקו כמחוות/חזותית.

## א. סיכום מספרי

| פרומפט | דרישות | PASS | PARTIAL | FAIL | MISSING | BLOCKED | FUTURE |
|---|---|---|---|---|---|---|---|
| פרומפט 1 (חלק 1) | 107 | 97 | 6 | 0 | 0 | 3 | 1 |
| פרומפט 2 (חלק 2) | 150 | 134 | 7 | 0 | 0 | 4 | 5 |
| **סה"כ** | **257** | **231** | **13** | **0** | **0** | **7** | **6** |

השורות אטומיות: דרישה אחת בפרומפט מפוצלת כשחלק ממנה ניתן לבדיקה וחלק לא (למשל 'התראת סיום מנוחה': לוגיקה = PASS, מסירה במכשיר = BLOCKED). `FUTURE` = אינטגרציות שהפרומפט עצמו אמר שאין צורך לממש (§15) ועוד שורה אחת בפרומפט 1 (אנגלית).

## ב. טבלת דרישות (סטטוס והוכחה)

הוכחה = בדיקות שרצו ועברו ב-CI; `D` domain, `R` Robolectric (נתונים/שירותים), `U` Compose UI על Robolectric, `B` בנייה, `C` קריאת קוד, `X` מכשיר (לא הורץ).

### פרומפט 1

| ID | סטטוס | דרישה | הוכחה |
|---|---|---|---|
| P1-1.01 | PASS | Kotlin + Jetpack Compose + Material 3 + MVVM | B: assembleDebug+assembleRelease; U: AppSmokeTest |
| P1-1.02 | PASS | Room/SQLite, Coroutines/StateFlow, Repository pattern | R: WorkoutRepositoryTest(34), DurabilityTest(5), SeederAndPlanTest(12) |
| P1-1.03 | PASS | Navigation Compose + DataStore | U: AppSmokeTest.bottom_tabs_open_their_screens, AppFlowsTest; R: SeederAndPlanTest (device flags) |
| P1-1.04 | PASS | Hilt - רק אם יש הצדקה | C: החלטה מתועדת ב-CLAUDE.md (אפליקציה קטנה, DI ידני, בנייה מהירה) |
| P1-1.05 | PASS | הפרדה בין UI, Domain, Data, Database ו-Services | B: מודול domain נבנה ונבדק בלי Android (-Pliftos.domainOnly); D: 287 בדיקות |
| P1-1.06 | PASS | מערכת מודולרית שניתנת להרחבה | D: AiProviderTest, FlootImporterTest, BackupV2Test |
| P1-1.07 | PASS | בדיקת תאימות וגרסאות ספריות לפני שימוש | B: בנייה ירוקה עם גרסאות נעוצות; CLAUDE.md מציין 'verified Oct 2026' |
| P1-2.01 | PASS | ממשק בעברית מלאה | U: ScreenSweepTest + AppSmokeTest מציגים טקסט עברי בכל המסכים המרכזיים |
| P1-2.02 | PASS | RTL | U: ScreenSweepTest.the_whole_app_is_laid_out_right_to_left |
| P1-2.03 | PASS | מצב כהה כברירת מחדל | U: ThemeTest.the_default_theme_is_dark_charcoal_with_a_lime_accent (ThemeMode.DARK כברירת מחדל) |
| P1-2.04 | PASS | רקע פחם + הדגשות ליים | U: ThemeTest.the_default_theme_is_dark_charcoal_with_a_lime_accent (בהירות הרקע, דומיננטיות ירוק בצבע ההדגשה) |
| P1-2.05 | PASS | כרטיסיות מעוגלות | U: ThemeTest.cards_and_surfaces_use_rounded_corners |
| P1-2.06 | PASS | אייקונים ברורים | U: חיפוש לפי contentDescription ('סמן כבוצע','דלג על המנוחה','הגדרות','חזרה','ערוך אימון') |
| P1-2.07 | PARTIAL | אנימציות עדינות | C; U: הזרימות רצות עם האנימציות בלי קריסה |
| P1-2.08 | PARTIAL | מסכים נקיים ומודרניים; תחושת מוצר מסחרי | U: רינדור מוצלח; לא נבדק חזותית |
| P1-2.09 | PASS | ניווט תחתון | U: AppSmokeTest.bottom_tabs_open_their_screens |
| P1-2.10 | PASS | כפתורים גדולים ונוחים בזמן אימון | U: ScreenSweepTest.the_main_buttons_are_big_enough_for_a_thumb |
| P1-2.11 | PARTIAL | הקלדה מהירה במקלדת מספרית | U: AppSmokeTest מקליד 60 ו-8; C: סוג המקלדת |
| P1-2.12 | PASS | מצב בהיר (עתידי) | U: ThemeTest.the_light_theme_is_really_light; ScreenSweepTest.the_light_theme_renders_the_home_screen_and_a_workout |
| P1-2.13 | FUTURE | אנגלית (עתידי) | C |
| P1-3.01 | PASS | האימון הבא | D: NextWorkoutResolverTest(7); U: AppSmokeTest.home_shows_the_next_workout_with_a_start_button |
| P1-3.02 | PASS | מספר אימונים שבוצעו השבוע והחודש | R: HomeDataTest.counts only finished workouts inside the date range; U: AppSmokeTest ('אימונים השבוע') |
| P1-3.03 | PASS | המשקל האחרון שתועד | R: HomeDataTest.the latest weight skips...; U: ScreenSweepTest.home_shows_the_data_of_the_week |
| P1-3.04 | PASS | אימון אחרון | R: HomeDataTest.the last workout is the newest finished one |
| P1-3.05 | PASS | מטרת האימון הנוכחית | D: DefaultPlanTableTest/LibraryAndPlanTest (goal='ירידה בשומן ושיפור מסת שריר'); C: הצגה בכרטיס |
| P1-3.06 | PASS | גישה מהירה לתוכניות | U: ScreenSweepTest.the_plan_shortcut_on_home_opens_the_plan |
| P1-3.07 | PASS | כפתור 'התחל אימון' | U: AppSmokeTest.a_workout_can_be_started_and_a_set_logged... |
| P1-3.08 | PASS | כפתור 'המשך אימון' כשיש אימון פעיל | U: ScreenSweepTest.a_running_workout_is_offered_for_continuing_on_home |
| P1-3.09 | PASS | נתונים מהמסד האמיתי בלבד, בלי נתונים פיקטיביים | D: DashboardTest.empty database gives empty cards; U: AppSmokeTest (מצב ריק 'עוד לא תועד משקל') |
| P1-4.01 | PASS | יצירה וניהול של כמה תוכניות | R: SeederAndPlanTest.create a plan, add a workout and exercises, then edit them; U: AppFlowsTest.plan_screens_open_down_to_the_exercise_editor |
| P1-4.02 | PASS | שינוי שם ותיאור | R: SeederAndPlanTest (create/edit) |
| P1-4.03 | PASS | הוספה, הסרה והחלפה של תרגילים | R: SeederAndPlanTest.exercises can be reordered, duplicated, swapped and removed |
| P1-4.04 | PARTIAL | שינוי סדר תרגילים בגרירה | R: הסדר החדש נשמר (reordered) |
| P1-4.05 | PASS | עריכת סטים, טווח חזרות, משקל יעד, RIR/RPE, מנוחה, הערות | R: SeederAndPlanTest.invalid planned values are clamped to sane limits; U: עורך התרגיל נפתח |
| P1-4.06 | PASS | שכפול תוכנית | R: SeederAndPlanTest.duplicate creates an independent deep copy |
| P1-4.07 | PASS | מחיקת תוכנית | R: SeederAndPlanTest.deleting a plan keeps workout history |
| P1-4.08 | PASS | שינוי ימי אימון | R: SeederAndPlanTest.workouts can be reordered and rescheduled |
| P1-4.09 | PASS | שמירת תוכנית כתבנית | R: SeederAndPlanTest.plans can be saved as a template and instantiated again |
| P1-4.10 | PASS | שינוי בתוכנית לא משנה אימונים היסטוריים | D: SessionFactoryTest.later plan edits do not change an existing session snapshot; R: WorkoutRepositoryTest.changing the plan afterwards does not alter an existing sessi… |
| P1-4.11 | PASS | תוכנית ברירת מחדל: 4 אימונים (Upper A / Lower A / Upper B / Lower B) עם התרגילים, הסטים ו… | D: DefaultPlanTableTest (טבלת הפרומפט מוקלדת ידנית ומושווית שורה-שורה), LibraryAndPlanTest.default plan matches the requested program; R: SeederAndPlanTest.first launch … |
| P1-4.12 | PASS | חלוקת שבוע: א' Upper A, ב' Lower A, ג' מנוחה, ד' Upper B, ה' Lower B | D: DefaultPlanTableTest.the week is Sunday Monday rest Wednesday Thursday; NextWorkoutResolverTest.on the rest day tuesday... |
| P1-4.13 | PASS | כל פרטי תוכנית ברירת המחדל ניתנים לעריכה/מחיקה | R: SeederAndPlanTest.a deleted default plan is never recreated; create/edit tests |
| P1-5.01 | PASS | טיימר זמן אימון | R: ServiceBehaviorTest.the_ongoing_notification_shows_the_rest_countdown_then_the_workout; U: AppSmokeTest |
| P1-5.02 | PASS | רשימת תרגילים, מספר סטים וטווחי חזרות | D: SessionFactoryTest.new session is active and snapshots plan data; U: AppSmokeTest |
| P1-5.03 | PASS | נתוני האימון הקודם מוצגים | R: WorkoutRepositoryTest.the next workout of the same type is pre-filled from the last one; an exercise never done before has no previous data |
| P1-5.04 | PASS | טעינה מראש של משקלי העבודה הקודמים | D: SessionFactoryTest.sets are pre-filled from the previous performance by index; without history the sets are empty |
| P1-5.05 | PASS | שורת סט: סט / משקל / חזרות / RIR / הושלם | U: AppSmokeTest.a_workout_can_be_started_and_a_set_logged... (60×8, סימון, שמירה במסד); ScenarioATest A5-A8 (משקל 60, חזרות 8, RIR 2 -> מסד) |
| P1-5.06 | PASS | עדכון משקל, חזרות ו-RIR | R: WorkoutRepositoryTest.updating weight reps and rir is saved immediately; out of range values are ignored; U: ScenarioATest A5-A7 |
| P1-5.07 | PASS | סימון סט כהושלם וביטול הסימון | R: a set without reps cannot be completed; D: SetEditingTest.completion toggles the timestamp; U: AppSmokeTest |
| P1-5.08 | PASS | הוספה ומחיקה של סטים | R: adding and deleting sets keeps order consistent; a new set copies the previous set values but is not completed |
| P1-5.09 | PASS | הפרדה בין סטי חימום לסטי עבודה | R: warm-up sets are separate from working sets and never trigger the rest timer; D: VolumeCalculatorTest.warm-up sets and incomplete sets are excluded |
| P1-5.10 | PASS | הוספת תרגיל תוך כדי אימון | R: an exercise can be added during the workout |
| P1-5.11 | PASS | החלפת תרגיל | R: an exercise can be replaced and keeps its position; replacing keeps sets that were already completed |
| P1-5.12 | PASS | שינוי סדר התרגילים | R: exercises can be reordered during the workout |
| P1-5.13 | PASS | הערות לתרגיל והערות כלליות לאימון | R: notes ratings and skip reason are stored |
| P1-5.14 | PASS | דירוג אנרגיה והרגשה | R: notes ratings and skip reason are stored |
| P1-5.15 | PASS | תיעוד תרגילים חד-צדדיים | D: SetEditingTest (3 בדיקות unilateral), SessionFactoryTest.unilateral exercise creates right and left rows; R: unilateral exercise rows come in right-left pairs |
| P1-5.16 | PASS | טיימר מנוחה לפי זמן המנוחה של התרגיל | R: completing a working set starts the rest timer with the exercise's rest time; auto rest can be switched off; D: RestTimerTest(7) |
| P1-5.17 | PASS | עצירה, המשך, +15/+30 שניות, דילוג | R: rest timer can pause resume add time and skip; U: AppSmokeTest (פס המנוחה עם '15' ו'דלג על המנוחה') |
| P1-5.18 | PASS | התראה/רטט בסיום מנוחה - לוגיקה (פעם אחת, לפי הגדרה והרשאה) | R: ServiceBehaviorTest (4 בדיקות: פעם אחת, זמן סיום מיושן, הגדרה כבויה, בלי הרשאה); WorkoutRepositoryTest.exactly one finisher wins |
| P1-5.19 | BLOCKED | התראה/רטט בסיום מנוחה - מסירה בפועל במכשיר אמיתי | X: דורש מכשיר Samsung + OS אמיתי |
| P1-5.20 | PASS | הטיימר פועל נכון בנעילת מסך/רקע - מנגנון (חותמות זמן מוחלטות + שירות + התראה גיבוי) | D: RestTimerTest.survives process death...; R: ServiceBehaviorTest.the_backup_rest_alarm_is_scheduled_for_the_end_time_and_can_be_cancelled; WorkoutRepositoryTest.exactl… |
| P1-5.21 | BLOCKED | הטיימר פועל נכון בנעילת מסך/רקע - התנהגות Samsung בפועל (Doze, חיסכון בסוללה) | X |
| P1-5.22 | PASS | שמירה אוטומטית של כל שינוי | R: updating weight reps and rir is saved immediately; U: AppSmokeTest (המשקל והחזרות נקראים ישירות מהמסד אחרי הקלדה) |
| P1-5.23 | PASS | שחזור אימון פעיל אחרי יציאה/סגירה לא צפויה | R: WorkoutRepositoryTest.an active workout survives the process being killed and is fully restorable; DurabilityTest.data written before a restart is still there; U: Scr… |
| P1-5.24 | PASS | מסך סיכום: משך, תרגילים, סטים, משקלים/חזרות, נפח, הערות, השוואה לאימון קודם | U: AppFlowsTest.finishing_a_workout_opens_the_summary... (נפח 480); D: ProgressTest.comparison reports volume change versus previous session |
| P1-6.01 | PASS | לפחות 100 תרגילים | D: LibraryAndPlanTest.library has at least 100 exercises; U: AppSmokeTest ('104 תרגילים') |
| P1-6.02 | PASS | לכל תרגיל: שם עברית+אנגלית, שרירים ראשי/משני, ציוד, הוראות שלב-שלב, טעויות, בטיחות, חלופות | D: LibraryAndPlanTest.library passes every consistency rule (דורש ≥3 שלבים, ≥2 טעויות, בטיחות, חלופות קיימות, שם בשתי שפות) |
| P1-6.03 | PASS | בלי קישורי יוטיוב מומצאים; חיפוש ממוקד כשאין סרטון מאומת | D: built-in exercises never carry an invented video link; youtube search link is a search; U: AppFlowsTest.library_row_opens_the_exercise_page_with_honest_video_search |
| P1-6.04 | PASS | חיפוש וסינון לפי קבוצת שרירים, ציוד ושם | D: ExerciseSearchTest(8); U: AppSmokeTest.library_search_filters_the_list |
| P1-6.05 | PASS | תרגילים אישיים עם שם, הסבר וקישור | R: SeederAndPlanTest.custom exercise lifecycle; reseeding the library never touches custom exercises |
| P1-7.01 | PASS | כל אימון שהסתיים נשמר במסד | R: finishing removes unfinished sets...; history list totals match the domain volume calculation |
| P1-7.02 | PASS | פתיחת אימון קודם: תאריך/שעה, סוג, משך, תרגילים, סטים, משקלים, RIR, הערות, נפח | U: ScreenSweepTest.a_workout_from_the_history_opens_its_summary_and_the_ai_screen; AppFlowsTest.finishing_a_workout... |
| P1-7.03 | PASS | עריכת אימון אחרי שהסתיים (תיקון משקל גם שבוע אחרי) | R: WorkoutRepositoryTest.a wrong weight can be corrected after the workout and volume follows; D: VolumeCalculatorTest.editing a historical weight...; U: ScreenSweepTest… |
| P1-7.04 | PASS | הזנת אימון שבוצע בעבר עם תאריך היסטורי | R: a past workout can be entered with a historical date and time; start and end time can be edited and the date follows |
| P1-8.01 | PASS | מדידות: משקל, מותניים, חזה, זרוע, ירך, אגן, צוואר, אחוז שומן, תאריך, הערות | R: MeasurementTest.all metrics are stored and a note only entry is allowed |
| P1-8.02 | PASS | לא חובה למלא את כל השדות | R: MeasurementTest.not every metric has to be entered |
| P1-8.03 | PASS | עריכה ומחיקה של מדידות | R: a measurement can be edited and its date moved; ... deleted and stays gone; U: AppFlowsTest.a_body_measurement_can_be_added_from_the_body_tab |
| P1-8.04 | PASS | המדידות נשמרות לצמיתות עד מחיקה יזומה | R: DurabilityTest.data written before a restart is still there after it; BackupRepositoryTest |
| P1-9.01 | PASS | מודל נתונים: Exercise, WorkoutPlan, PlannedExercise, WorkoutSession, SessionExercise, Exe… | B: סכימה מיוצאת; R: DurabilityTest.the exported schema for the current version is committed |
| P1-9.02 | PASS | מפתחות יציבים, קשרי גומלין, אינדקסים וטרנזקציות | R: DurabilityTest.foreign keys are enforced by the database; BackupRepositoryTest.a failure in the middle of an import rolls everything back |
| P1-9.03 | PASS | מיגרציות בלי מחיקת נתונים | R: MigrationTest.upgrading from version 1 keeps every row...; DurabilityTest.there is a migration for every version step; an unexpected newer database version fails safe… |
| P1-10.01 | PASS | ייצוא כל הנתונים ל-JSON עם גרסת Schema ברורה | D: BackupTest.backup declares an explicit schema version and format; R: BackupRepositoryTest.export then restore into a fresh install gives identical data |
| P1-10.02 | PASS | ייבוא מגיבוי JSON | R: BackupRepositoryTest (10 בדיקות) |
| P1-10.03 | PASS | ייצוא אימונים ל-CSV | D: ExportTest (csv); R: BackupArchiveTest/BackupRepositoryTest.csv and text exports can be produced from stored data |
| P1-10.04 | PASS | ייצוא אימון בודד | D: ExportTest.single workout json round trips including the previous workout; PeriodExportTest |
| P1-10.05 | PASS | העתקת סיכום אימון כטקסט | D: ExportTest.workout text contains the essentials; U: AppFlowsTest (לוח) |
| P1-10.06 | PASS | שיתוף קבצים דרך Android Share Sheet - בניית ה-Intent | R: ShareIntentsTest (כתובת content://, הרשאת קריאה, BOM) |
| P1-10.07 | BLOCKED | שיתוף קבצים - חלון הבחירה של המערכת והאפליקציות המקבלות | X |
| P1-10.08 | PARTIAL | בחירת קובץ מ-Google Drive דרך ממשק הקבצים של Android | C |
| P1-10.09 | PASS | אימות קובץ לפני ייבוא | D: BackupTest (invalid input, newer schema, duplicate ids, dangling references, implausible values); R: an invalid or foreign file is rejected before anything changes |
| P1-10.10 | PASS | אישור לפני דריסת נתונים + גיבוי בטיחות | R: restoring over existing data replaces it and writes a safety copy first; BackupArchiveTest |
| P1-10.11 | PASS | כפתור 'העתק דוח לניתוח ב-ChatGPT' | U: AppFlowsTest.finishing_a_workout... (הלוח מכיל את הדוח וההוראה בסופו); ScreenSweepTest.the_quick_copy_report... |
| P1-10.12 | PASS | הדוח מכיל את נתוני האימון ואת האימון הקודם | D: ExportTest.ai report includes previous workout, comparison and the exact request; ai report says plainly when there is no previous workout |
| P1-10.13 | PASS | אין צורך ב-API בתשלום | R: ManifestPolicyTest (בלי INTERNET, בלי קוד רשת) |
| P1-11.01 | PASS | בדיקות לוגיקה: יצירת תוכנית, הוספת תרגיל, עדכון סט, שמירת אימון, שחזור אימון פעיל, נפח, מ… | ראה P1-4.*, P1-5.*, P1-8.*, P1-9.*, P1-10.* |
| P1-11.02 | PASS | הרצת הבדיקות בפועל | B: ריצת CI (ראה FINAL_ACCEPTANCE_REPORT.md) |
| P1-11.03 | PASS | האפליקציה מתקמפלת והמסכים המרכזיים עובדים | B + U: AppSmokeTest, AppFlowsTest, ScreenSweepTest |
| P1-12.01 | PASS | פרויקט Android תקין הניתן לפתיחה ב-Android Studio | B: ה-Gradle wrapper בונה את הפרויקט ב-CI |
| P1-12.02 | PASS | Gradle Build עם תיקון שגיאות | B |
| P1-12.03 | PASS | Debug APK מוכן להתקנה | B: ארטיפקט liftos-debug-apk + aapt/sha256 בלוג CI |
| P1-12.04 | PARTIAL | Release APK והסבר חתימה | B: assembleRelease עובר |
| P1-12.05 | PASS | אין לטעון שקיים APK שלא נבנה | B: הארטיפקטים נבדקים דרך GitHub API (digest) ובלוג CI |
| P1-13.01 | PASS | README, ARCHITECTURE, DATA_MODEL, DEVELOPMENT_PLAN, CHANGELOG, CLAUDE.md | C: הקבצים קיימים ומעודכנים (נבדק ידנית בביקורת) |
| P1-13.02 | PASS | שימוש ב-Git | C |
| P1-14.01 | PASS | לפתח בפועל, לא רק תוכנית; לא למחוק יכולות עובדות | C: מספר הבדיקות רק עלה (95 -> ראה דוח) |
| P1-14.02 | PASS | דיווח בסיום חלק 1 (מה עובד, מה עבר, איפה ה-APK, מה נשאר) | C |

### פרומפט 2

| ID | סטטוס | דרישה | הוכחה |
|---|---|---|---|
| P2-0.01 | PASS | מיקוד בחדר כושר בלבד; אין מודול תזונה/קלוריות/ארוחות | R: ManifestPolicyTest.there is no nutrition module (סריקת מקורות ושמות קבצים) |
| P2-1.01 | PASS | המשך על הפרויקט הקיים (ללא פרויקט חדש), שמירת יכולות ונתונים קיימים | R: MigrationTest (נתוני v1 נשמרים); בדיקות חלק 1 ממשיכות לעבור |
| P2-1.02 | PASS | הפרדה בין הושלם/חלקי/טרם פותח | C |
| P2-1.03 | PASS | שינוי מסד נתונים רק עם Migration שמור נתונים | R: MigrationTest.upgrading from version 1 keeps every row and adds sensible defaults; DurabilityTest |
| P2-2.01 | PASS | המשקל הגבוה ביותר שהורם, לכל תרגיל | D: ExerciseAnalyticsTest.overview separates logged singles from estimates and finds rep maxes |
| P2-2.02 | PASS | מספר חזרות בכל משקל | D: ExerciseAnalyticsTest.weight table groups reps per weight; U: ScreenSweepTest.the_exercise_screen_shows... ('חזרות בכל משקל') |
| P2-2.03 | PASS | משקל ממוצע לסטי עבודה (אם שימושי) | D: ExerciseAnalyticsTest.average working weight is weighted by reps when weights differ; U: ScreenSweepTest.the_exercise_screen_shows_average_working_weight... |
| P2-2.04 | PASS | שיאים אישיים בעמוד התרגיל | D: RecordsTest; U: ScreenSweepTest (עמוד תרגיל) |
| P2-2.05 | PASS | Estimated 1RM (Epley) מסומן במפורש כהערכה | D: OneRepMaxTest.epley formula; refuses unreliable or invalid inputs; ExerciseAnalyticsTest.overview separates logged singles from estimates |
| P2-2.06 | PASS | הערכת 1RM לא מוצגת כשיא שנמדד; Actual 1RM רק אם בוצעה חזרה בודדת | D: ExerciseAnalyticsTest.no actual 1RM is reported when no single was logged |
| P2-2.07 | PASS | משקל מקסימלי ל-5, 8, 10 חזרות | D: ExerciseAnalyticsTest.overview separates logged singles from estimates and finds rep maxes; RecordsTest.rep max records use the heaviest weight for at least n reps |
| P2-2.08 | PASS | התקדמות ביחס לאימון הקודם | D: ExerciseAnalyticsTest.last versus previous gives a verdict; ProgressTest.identical performance is reported as the same - never as improvement |
| P2-2.09 | PASS | התקדמות לאורך חודש, 3 חודשים ושנה | D: ExerciseAnalyticsTest.trend needs two workouts in the window and reports first versus last |
| P2-2.10 | PASS | היסטוריית כל הסטים שבוצעו | U: ScreenSweepTest.the_exercise_screen_shows_average_working_weight... |
| P2-2.11 | PASS | משקלים מומלצים לאימון הבא | D: ProgressionEngineTest(19) |
| P2-2.12 | PASS | עמוד מפורט לתרגיל נבחר (למשל Bench Press) | U: ScreenSweepTest.the_exercise_screen_shows_average_working_weight_and_the_volume_of_each_set |
| P2-2.13 | PASS | Volume Load = משקל × חזרות; נפח לכל סט / תרגיל / אימון | D: VolumeCalculatorTest(6); ExerciseAnalyticsTest.session stats count volume...; U: AppFlowsTest (480) |
| P2-2.14 | PASS | נפח שבועי וחודשי; מספר סטי עבודה; השוואה לתקופה קודמת | D: VolumeAnalyticsTest(9): period stats, comparison against the previous equal period, buckets keep empty weeks |
| P2-2.15 | PASS | סטים ואימונים לכל קבוצת שרירים, עם הצגת הנחות המודל | D: VolumeAnalyticsTest.direct and indirect muscle sets follow the stated model; warm-ups never count; U: AnalyticsScreen מציג ASSUMPTIONS |
| P2-2.16 | PASS | הבחנה בין נפח בק"ג-חזרות לסטים אפקטיביים; זוג שמאל+ימין = סט אחד | D: ExerciseAnalyticsTest.a unilateral pair counts as one set but volume sums both sides |
| P2-2.17 | PASS | דשבורד: אימונים השבוע, סטים, נפח, שיאים חדשים, שרירים שאומנו, עמידה בתוכנית, מגמת כוח, זמ… | D: DashboardTest(6); U: ScreenSweepTest.the_progress_tab_shows_charts_muscles_insights_and_the_exercise_list |
| P2-2.18 | PASS | הכול מחושב בזמן אמת מהמסד; אין נתוני דמו | R: AnalyticsLiveUpdateTest (5); D: DashboardTest.empty database gives empty cards, not made-up numbers |
| P2-3.01 | PASS | גרפי אימונים: משקל עבודה לכל תרגיל, 1RM משוער לאורך זמן, חזרות במשקל קבוע | D: ChartSeriesTest.metric points carry exact value, date and workout; reps at a fixed weight use the best set of each workout |
| P2-3.02 | PASS | גרפי אימונים: נפח לכל אימון, נפח שבועי, סטי עבודה, אימונים בשבוע, משך אימון | D: ChartSeriesTest.bucket series...; workout volume points skip workouts without volume; U: ScreenSweepTest.every_chart_choice_can_be_selected |
| P2-3.03 | PASS | חלוקת אימונים לפי קבוצות שרירים | D: ChartSeriesTest.distribution fractions add up to one |
| P2-3.04 | PASS | התקדמות לעומת תקופות קודמות | D: VolumeAnalyticsTest.comparison against the previous equal period |
| P2-3.05 | PASS | גרפי גוף: משקל, ממוצע נע 7 ימים, היקפים, אחוז שומן | D: BodyAnalyticsTest.series keeps only measurements that have the field; ProgressTest.moving average smooths daily noise |
| P2-3.06 | PASS | השוואת שתי תקופות במדדי גוף | D: BodyAnalyticsTest.period averages are compared |
| P2-3.07 | PASS | מסנני תקופה: 7/30/90 ימים, 6 חודשים, שנה, כל התקופה, טווח מותאם | D: VolumeAnalyticsTest.periods resolve to concrete ranges; granularity grows with the range |
| P2-3.08 | PARTIAL | לחיצה על נקודה בגרף מציגה ערך מדויק, תאריך ואימון | D: ChartSeriesTest (הנתונים לנקודה: ערך, תאריך, אימון); C: ההקשבה ללחיצה |
| P2-3.09 | PASS | הגרפים מתעדכנים מיד בעקבות שינוי באימון/מדידה | R: AnalyticsLiveUpdateTest (עריכת סט, ביטול סימון, מחיקת אימון, flow מול קריאה חד-פעמית) |
| P2-3.10 | PASS | אין מספיק נתונים -> הודעה ולא גרף פיקטיבי | D: DashboardTest.no trend is claimed without data in both windows; U: AppSmokeTest ('עוד אין נתוני אימונים') |
| P2-4.01 | PASS | Double Progression כשיטה מרכזית (הדוגמה מהפרומפט -> 62.5 ק"ג) | D: ProgressionEngineTest.the double progression example from the brief suggests 62_5 kg |
| P2-4.02 | PASS | התחשבות בטווח חזרות, סטים, משקל, RIR/RPE, היסטוריה, כישלון, ירידה, סוג תרגיל, קפיצת משקל,… | D: ProgressionEngineTest (before top range, failure, declines, increments by equipment, low energy, long break, missing sets, pyramids, bodyweight, holds) |
| P2-4.03 | PASS | המלצות מילוליות (שקול העלאה / שמור משקל והוסף חזרה / בדוק התאוששות) | D: ProgressionEngineTest.before the top of the range the advice is to add reps...; two consecutive declines ask to check recovery |
| P2-4.04 | PASS | כל המלצה כוללת הסבר | D: ProgressionEngineTest.every kind of suggestion carries an explanation |
| P2-4.05 | PASS | אפשר להתעלם מהמלצה | D: ProgressionEngineTest.an ignored suggestion stays hidden only until the next workout |
| P2-4.06 | PASS | שינוי משקל יעד ידני + מדרגת משקל לתרגיל | D: ProgressionEngineTest.increments follow the equipment and can be overridden; plan targets override the snapshotted ones |
| P2-4.07 | PASS | אין העלאת משקל אוטומטית ללא אישור | D: SessionFactoryTest.sets are pre-filled from the previous performance by index (ערכי האימון הקודם, לא ההצעה); C: ניתוח נתיבי הקוד - applyWeightToOpenSets מופעל רק ממסל… |
| P2-4.08 | PASS | אימון בודד גרוע לא מוביל למסקנה שהמשתמש נחלש | D: ProgressionEngineTest.one bad workout never lowers the target; a deliberate weight reduction is not treated as a decline |
| P2-4.09 | PASS | בלי מספיק נתונים - המלצה מסומנת כביטחון נמוך / אין המלצה | D: ProgressionEngineTest.a single workout is a low-confidence suggestion that says so; no history gives no recommendation |
| P2-5.01 | PASS | שיאים: משקל מקסימלי, 1RM בפועל, 5 חזרות, 10 חזרות, 1RM משוער, חזרות במשקל, נפח לתרגיל | D: RecordsTest(12) |
| P2-5.02 | PASS | האימון הראשון הוא בסיס, תיקו אינו שיא | D: RecordsTest.the first workout creates a baseline, not a record; a heavier weight is a record and a tie is not |
| P2-5.03 | PASS | חימום אינו קובע שיא | D: RecordsTest.warm-ups cannot set records |
| P2-5.04 | PASS | הודעה 'שיא אישי חדש!' לא מוגזמת + גביע ליד התרגיל | D: RecordsTest.live detection compares the active workout against earlier history only; headline keeps the most important kinds |
| P2-5.05 | PASS | מסך שיאים מרכזי עם פתיחת שיא לאימון שבו נקבע | U: ScreenSweepTest.the_shortcuts_open_records_goals_and_recovery |
| P2-5.06 | PASS | עריכת היסטוריה מחשבת מחדש שיאים בעקביות | D: RecordsTest.editing history recomputes consistently; R: AnalyticsLiveUpdateTest.editing a past set changes the volume and the record that workout set |
| P2-6.01 | PASS | תצוגות: יומית, שבועית, חודשית, רשימה, היסטוריה | U: ScreenSweepTest.all_five_journal_views_open_and_the_history_lists_the_workouts |
| P2-6.02 | PASS | סוגי אירועים (אימון מתוכנן/הושלם/הוחמץ, מנוחה, אירובי, הליכה, ריצה, שחייה, רכיבה, ספורט, … | D: CalendarTest.rest days and single events are classified; R: CalendarRepositoryTest |
| P2-6.03 | PASS | יצירה/עריכה/מחיקה של אירוע | R: CalendarRepositoryTest.activity events can be saved and removed; U: AppFlowsTest.an_event_added_in_the_journal_is_saved_and_shown |
| P2-6.04 | PASS | שינוי תאריך/שעה/משך, הערות | R: CalendarRepositoryTest.an event is stored with its local date and can be deleted |
| P2-6.05 | PASS | אירועים חוזרים | D: CalendarTest (weekly, every other week, daily with until/count, monthly clamping, exceptions, text round trip); R: marking one occurrence... leaves the rest of the se… |
| P2-6.06 | PASS | העברת אימון ליום אחר בלי לשנות היסטוריה | D: CalendarTest.moving a planned workout shows it on the new day only; moving a plan day does not touch recorded workouts; R: moving a planned workout never touches reco… |
| P2-6.07 | PASS | סימון אימון שלא בוצע (מפורש, לא אוטומטי) | D: CalendarTest.skipped and missed marks are explicit and never automatic; a recent past planned day without a workout is flagged as missed |
| P2-6.08 | PASS | חיבור אירוע לאימון מתועד | R: CalendarRepositoryTest.linking an occurrence to a workout marks it done and can be undone |
| P2-6.09 | PASS | צבעים/סימונים להבדלה בין מתוכנן, בוצע ויום מנוחה | D: CalendarTest.markers summarise each day for the month grid |
| P2-7.01 | PASS | שדות: משקל, מותניים, חזה, זרוע, ירך, אגן, צוואר, כתפיים, אחוז שומן, הערות, תאריך ושעה | R: MeasurementTest(8); MigrationTest |
| P2-7.02 | PASS | שדות מדידה מותאמים אישית | R: BackupArchiveTest.a zip backup carries ... custom fields; D: BodyAnalyticsTest.custom metrics have their own series |
| P2-7.03 | PASS | השוואת מדידות, שינוי מוחלט ובאחוזים | D: BodyAnalyticsTest.two measurements compare field by field with percent |
| P2-7.04 | PASS | ממוצע משקל נע והשוואת תקופות | D: ProgressTest.moving average smooths daily noise; BodyAnalyticsTest.period averages are compared |
| P2-7.05 | PASS | עריכת מדידות ישנות ומחיקה לאחר אישור | R: MeasurementTest (edit/delete) |
| P2-7.06 | PASS | תיעוד נסיבות המדידה | R: MeasurementTest.all metrics are stored...; MigrationTest (conditions נוספה) |
| P2-7.07 | PASS | הבחנה בין תנודות יומיות למגמה ארוכת טווח; בלי מסקנות על שומן/שריר | D: BodyAnalyticsTest.a steady decline is a falling trend; daily noise without a direction is stable; too few readings give no trend |
| P2-7.08 | PASS | אזהרה עדינה על ערך חריג | D: BodyAnalyticsTest.unusual values trigger a gentle warning |
| P2-8.01 | PARTIAL | צילום או בחירה מהגלריה | R: ShareIntentsTest לא רלוונטי; U: PhotoPipelineTest מכסה את קליטת הקובץ |
| P2-8.02 | PASS | סוגי צילום (חזית/צד/גב) + תאריך + משקל + הערות | U: PhotoPipelineTest.details can be edited and a photo can be deleted with its file; ScreenSweepTest (תמונות ברשימה) |
| P2-8.03 | PASS | הקטנה וקידוד JPEG, סיבוב EXIF, הסרת מטא-דאטה | U: PhotoPipelineTest (הקטנה ל-≤2048, שמירת יחס, סיבוב EXIF, קובץ שאינו תמונה נדחה) |
| P2-8.04 | PARTIAL | מסך השוואה: זו לצד זו / לפני-אחרי / Slider | U: ScreenSweepTest (כפתור 'השוואת תמונות' קיים); C |
| P2-8.05 | PASS | שמירה פרטית; אין שליחה לשירות חיצוני | R: ManifestPolicyTest; U: PhotoPipelineTest.a big photo is stored ... in private storage |
| P2-8.06 | PASS | ייצוא התמונות כחלק מגיבוי מלא | R: BackupArchiveTest.a zip backup carries photos goals and custom fields into a fresh install |
| P2-9.01 | PASS | סוגי יעדים: משקל גוף, היקף, משקל בתרגיל, חזרות בתרגיל, אימונים בחודש/בשבוע, השלמת תוכנית,… | D: GoalEngineTest(12) |
| P2-9.02 | PASS | שדות יעד: שם, קטגוריה, ערך התחלתי/נוכחי/יעד, תאריכים, אחוז התקדמות, היסטוריית שינויים, סט… | D: GoalEngineTest; U: ScreenSweepTest (יעד מוצג); R: BackupV2Test.a version 2 backup round trips goals |
| P2-9.03 | PASS | יצירת יעד והשלמת יעד (חיווי הצלחה) | D: GoalEngineTest.a goal is achieved when the target is reached in the right direction; newly achieved lists only active goals that reached the target |
| P2-9.04 | PASS | אין התקדמות שאינה מגובה בנתונים | D: GoalEngineTest.no data means no progress, not zero percent |
| P2-9.05 | PASS | מסך יעדים (יצירה/עריכה/השהיה/מחיקה) | U: ScreenSweepTest.the_shortcuts_open_records_goals_and_recovery |
| P2-10.01 | PASS | דירוגים לאחר אימון: אנרגיה, קושי, איכות, כאבי שרירים, עייפות, שינה, הערות - אופציונליים | R: WorkoutRepositoryTest.notes ratings and skip reason are stored; MigrationTest (עמודות חדשות) |
| P2-10.02 | PASS | ניתוחים: שינויי אנרגיה, תדירות, ימי מנוחה, עומס שבועי, מגמות עייפות | D: RecoveryTest(8); U: ScreenSweepTest.the_shortcuts_open_records_goals_and_recovery |
| P2-10.03 | PASS | הצעה לבחון Deload - בלי אבחנה רפואית ובלי קביעת אימון יתר | D: RecoveryTest.a single signal does not suggest a deload; high fatigue and short sleep are reported as signals not diagnoses |
| P2-10.04 | PASS | מסך קצר ושימושי, לא שאלון רפואי | U: ScreenSweepTest |
| P2-11.01 | PASS | כפתור מרכזי 'נתח את האימון עם AI' בסיכום האימון | U: AppFlowsTest.finishing_a_workout_opens_the_summary... |
| P2-11.02 | PASS | העתקת דוח מפורט ללוח | R: ShareIntentsTest.copy puts the text on the clipboard; U: AppFlowsTest (לוח) |
| P2-11.03 | PASS | שיתוף דרך Android Share Sheet | R: ShareIntentsTest |
| P2-11.04 | PASS | ייצוא JSON / CSV / TXT | D: PeriodExportTest, ExportTest; R: BackupRepositoryTest.csv and text exports can be produced from stored data |
| P2-11.05 | BLOCKED | ייצוא PDF - הפקת קובץ | D: ReportBlocksTest (חלוקה לעמודים); U: PdfExportAttemptTest - נוסה בגרפיקה native ודולג (PdfDocument לא שמיש ב-Robolectric) |
| P2-11.06 | PASS | פתיחת ChatGPT/Claude באמצעות שיתוף נתמך - בניית ה-Intent ללא טענת שליחה | R: ShareIntentsTest (שיתוף ישיר רק לחבילה היעד; חבילה לא מותקנת מחזירה false) |
| P2-11.07 | BLOCKED | פתיחת ChatGPT/Claude בפועל במכשיר | X |
| P2-11.08 | PASS | אין טענה שהמידע נשלח ישירות ל-AI כשהוא רק הועתק/שותף | D: AiProviderTest.no provider is connected, so the app never claims to send data; U: AppFlowsTest ('האפליקציה לא שולחת שום מידע') |
| P2-11.09 | PASS | תוכן הדוח: תוכנית, תאריך, התחלה/סיום, משך, תרגילים, סטי עבודה וחימום בנפרד, משקלים, חזרות… | D: ExportTest; WorkoutReportExtrasTest.recovery ratings appear when they were entered |
| P2-11.10 | PASS | הדוח כולל השוואה לאימון קודם, שיאים חדשים ונתוני מגמות רלוונטיים | D: WorkoutReportExtrasTest(6); R/U: ScreenSweepTest.the_quick_copy_report_of_a_workout_has_its_records_its_trends_and_the_instruction |
| P2-11.11 | PASS | נתוני גוף והערות אישיות רק לפי בחירה | D: ExportTest.body data appears only when explicitly attached; PeriodReportTest.body data and notes only appear when chosen; PeriodExportTest.without notes removes every… |
| P2-11.12 | PASS | הוראת הבקשה ל-AI בנוסח הפרומפט, בסוף הדוח | D: PeriodReportTest.the report ends with the exact analysis request; WorkoutReportExtrasTest.the exact request still ends the report |
| P2-11.13 | PASS | ייצוא תקופתי: אימון בודד / שבוע / חודש / 12 שבועות / מותאם / כל ההיסטוריה | D: PeriodReportTest.scopes resolve to date ranges |
| P2-11.14 | PASS | בחירה מה לכלול בדוח (גוף, הערות, התאוששות, יעדים, שיאים, הצעות) | D: PeriodReportTest; U: ScreenSweepTest (מסך AI נפתח) |
| P2-11.15 | FUTURE | שכבת אינטגרציית API עתידית (OpenAI/Anthropic): מפתח מאובטח, הסכמה, שליטה, כיבוי | D: AiProviderTest.a request cannot be built without a recorded consent |
| P2-12.01 | PASS | מנוע תובנות מקומי מבוסס חישוב (העלאת משקל, 4 אימונים בשבוע, נפח עלה, שריר שלא אומן, ירידה… | D: InsightEngineTest(6) |
| P2-12.02 | PASS | 'נדרשים אימונים נוספים כדי לזהות מגמה' כשאין מספיק נתונים | D: InsightEngineTest.with too little history it says so and invents no trend |
| P2-12.03 | PASS | לא לקרוא לחישובים AI | D: InsightEngineTest.no insight mentions artificial intelligence; U: AnalyticsScreen ('אין כאן בינה מלאכותית') |
| P2-13.01 | PASS | תזכורות: אימון מתוכנן, שקילה, היקפים, סיכום שבועי, גיבוי - כל סוג בנפרד | D: ReminderPlannerTest(15); R: ServiceBehaviorTest (switching on/off, one alarm each, content of weigh-in/weekly summary) |
| P2-13.02 | PASS | סיום זמן מנוחה (מתג נפרד) | R: ServiceBehaviorTest.the_rest_alert_respects_the_notification_setting_but_still_ends_the_rest |
| P2-13.03 | PASS | יעד שהושג ושיא אישי חדש - התראה לפי העדפות | R: ServiceBehaviorTest.reaching_a_goal_posts_one_notification_and_marks_it_achieved; the_goal_notification_has_its_own_switch...; a_new_record_notification_uses_the_achi… |
| P2-13.04 | PASS | כיבוד הרשאת POST_NOTIFICATIONS | R: ServiceBehaviorTest.without_the_notification_permission_nothing_is_posted_and_nothing_crashes |
| P2-13.05 | PASS | כיבוד Do Not Disturb | R: ServiceBehaviorTest.the_notification_channels_respect_do_not_disturb (אף ערוץ לא עוקף DND) |
| P2-13.06 | PASS | תזמון אמין: Alarm עם setAndAllowWhileIdle, בניית מחדש בהפעלה ואחרי אתחול | R: ServiceBehaviorTest (alarm נוצר לעתיד, allowWhileIdle, בוטל); D: ReminderPlannerTest.reminders keep their wall clock time across the daylight saving change |
| P2-13.07 | BLOCKED | מסירת התראה/תזכורת בפועל במכשיר, כולל Doze וחיסכון בסוללה | X |
| P2-14.01 | PASS | גיבוי מלא: תוכניות, תרגילים מותאמים, אימונים, סטים, שיאים (נגזרים), מדדי גוף, יעדים, אירו… | R: BackupRepositoryTest.export then restore into a fresh install gives identical data; BackupArchiveTest.a zip backup carries photos goals and custom fields; D: BackupV2… |
| P2-14.02 | PASS | פורמטים: JSON עם Schema ו-ZIP עם מדיה | R: BackupArchiveTest(7); D: BackupTest |
| P2-14.03 | PASS | ייצוא, בחירת מיקום שמירה, שיתוף, שחזור | R: BackupRepositoryTest/ShareIntentsTest; C: CreateDocument/OpenDocument |
| P2-14.04 | PASS | תצוגה מקדימה לפני ייבוא, אימות תקינות, זיהוי כפילויות | D: BackupTest (validation); R: BackupArchiveTest.merging the same archive again reports everything as already existing |
| P2-14.05 | PASS | מניעת דריסה לא מכוונת + גיבוי נוסף לפני שחזור | R: BackupRepositoryTest.restoring over existing data replaces it and writes a safety copy first; merge adds only what is missing and is idempotent |
| P2-14.06 | PASS | טיפול במיגרציות בין גרסאות גיבוי | D: BackupV2Test.a version 1 backup without the new tables still loads; BackupTest.a backup from a newer schema is refused |
| P2-14.07 | PASS | קובץ גיבוי פגום מטופל בבטחה, ושמות קבצים מסוכנים בארכיון נחסמים | D: BackupTest.a corrupt native backup fails cleanly; BackupV2Test.photo file names that could escape the photo folder are rejected; R: BackupArchiveTest.an archive witho… |
| P2-14.08 | PARTIAL | Import Adapter ל-JSON של Floot (version, plans, customExercises, sessions, measurements, … | D: FlootImporterTest(12) על מבנה שנבנה מתיאור המשתמש |
| P2-15.01 | FUTURE | Health Connect | — |
| P2-15.02 | FUTURE | Samsung Health / שעונים חכמים / צעדים / דופק / שינה | — |
| P2-15.03 | FUTURE | ווידג'ט Android לאימון הבא | D: NextWorkoutResolverTest |
| P2-15.04 | FUTURE | סנכרון Google Calendar / גיבוי לענן / מספר מכשירים / ייבוא מאפליקציות אחרות | D: FlootImporterTest (מנגנון adapter קיים) |
| P2-15.05 | PASS | האפליקציה עובדת אופליין ואינה תלויה בשירות חיצוני | R: ManifestPolicyTest |
| P2-16.01 | PASS | מסך אימון: כפתורי +/- למשקל ולחזרות, מדרגות משקל לפי תרגיל | D: ProgressionEngineTest.increments follow the equipment and can be overridden; U: AppSmokeTest |
| P2-16.02 | PASS | סימון סט בלחיצה אחת; מעבר נוח לסט הבא; חיווי השלמת תרגיל; התקדמות כוללת | U: AppSmokeTest (סימון בלחיצה, פס מנוחה) |
| P2-16.03 | PASS | נתוני אימון קודם ליד הנוכחיים; טיימר מנוחה זמין; שמירה אוטומטית; המשך אימון | R/U: ראה P1-5.03, P1-5.17, P1-5.22, P1-5.23 |
| P2-16.04 | PARTIAL | מסך אימון לא עמוס | U: מבנה; C |
| P2-16.05 | PASS | הודעות שגיאה/הצלחה, מצבי טעינה ומצבים ריקים | U: AppSmokeTest ('עוד אין נתוני אימונים', 'עוד אין מדידות', 'עוד אין אימונים ביומן'); ScreenSweepTest |
| P2-16.06 | PASS | נגישות ויציבות לגודל גופן | U: ScreenSweepTest.home_and_a_new_workout_stay_usable_with_a_very_large_font (fontScale=2) |
| P2-16.07 | PASS | אין כפתורים שאינם מחוברים לפעולה | B: חיפוש בקוד אחרי 'onClick = {}' - 0 תוצאות; C |
| P2-17.01 | PASS | מאות אימונים ואלפי סטים (ביצועי חישוב) | D: LargeHistoryTest.history of five years is analysed quickly; calendar of a month is built quickly from a long history |
| P2-17.02 | PASS | שינוי תוכנית תוך כדי שימוש - אין השפעה על אימון פעיל | R: WorkoutRepositoryTest.changing the plan afterwards does not alter an existing session |
| P2-17.03 | PARTIAL | אימון פעיל לאורך זמן; מעבר לרקע; נעילת מסך; שיחה נכנסת; סגירה לא צפויה; הפעלה מחדש | R: an active workout survives the process being killed...; D: RestTimerTest.survives process death |
| P2-17.04 | PASS | עבודה ללא אינטרנט | R: ManifestPolicyTest |
| P2-17.05 | PASS | קובץ גיבוי פגום | D/R |
| P2-17.06 | PASS | מדידה עם ערך חריג | D: BodyAnalyticsTest.unusual values trigger a gentle warning |
| P2-17.07 | PASS | שינוי אזור זמן ושעון קיץ | D: TimeUtilsTest(6): local date uses the recorded zone; stable across the autumn daylight saving change; spring gap; R: MeasurementTest.late evening measurement keeps th… |
| P2-17.08 | PASS | שינוי תאריך של אימון ישן | R: WorkoutRepositoryTest.start and end time can be edited and the date follows |
| P2-17.09 | PASS | עקביות נתונים וטרנזקציות; אין מסך שמציג מידע שונה ממסך אחר | R: AnalyticsLiveUpdateTest.the live flow and the one-shot read agree after an edit; BackupRepositoryTest (rollback) |
| P2-18.01 | PASS | אחסון מקומי פרטי; allowBackup כבוי; רכיבים לא חשופים | R: ManifestPolicyTest.android auto backup is off and only the launcher activity of the app is exported |
| P2-18.02 | PASS | הרשאות Android מינימליות | R: ManifestPolicyTest.the app asks only for the permissions the timer needs... |
| P2-18.03 | PASS | הגנה על תמונות התקדמות; אי-העברה לשירותים חיצוניים ללא אישור | R: ManifestPolicyTest; U: PhotoPipelineTest |
| P2-18.04 | PASS | שליטה מלאה בייצוא; מניעת חשיפת מפתחות API | R: ManifestPolicyTest.the source has no network code; D: AiProviderTest |
| P2-18.05 | PASS | אפשרות למחוק מידע מהאפליקציה | R: BackupArchiveTest.delete everything removes personal data and photo files, then restores the starting state; U: DeleteConfirmationTest (כלל האישור) |
| P2-19.01 | PASS | הרחבת הבדיקות לכל הנושאים ברשימה וביצוע בפועל | ראה P2-2.* עד P2-18.* |
| P2-19.02 | BLOCKED | בדיקת ייצוא PDF | D: ReportBlocksTest (חלוקה לעמודים בלבד); U: PdfExportAttemptTest דולג |
| P2-19.03 | PASS | בדיקות UI מתאימות | U: AppSmokeTest(5), AppFlowsTest(5), ScreenSweepTest, PhotoPipelineTest |
| P2-20.01 | PASS | סקירת קוד, אין שגיאות קומפילציה, הסרת קוד שאינו בשימוש | B: קומפילציה ירוקה; C |
| P2-20.02 | PASS | בדיקות רגרסיה, מסכי האפליקציה, שמירה, ייבוא/ייצוא | B + U + R |
| P2-20.03 | PASS | APK מעודכן + הוראות התקנה ל-S26 Ultra | B: ארטיפקטים; C: הוראות |
| P2-20.04 | PARTIAL | Release חתום - הסבר מה נדרש | B |
| P2-20.05 | PASS | לא לטעון בדיקה פיזית שלא בוצעה | C |
| P2-21.01 | PASS | עדכון README, CLAUDE, ARCHITECTURE, DATA_MODEL, FEATURES, DEVELOPMENT_PLAN, CHANGELOG, TE… | C |
| P2-22.01 | PASS | בצע בפועל; אל תמחק יכולות; אל תציג כפתורים לא מחוברים או נתונים פיקטיביים | ראה P2-16.07, P2-2.18 |
| P2-22.02 | PASS | כשיש תלות בהרשאות/שירות חיצוני - להסביר ולהמשיך ברכיבים העצמאיים | C |
| P2-23.01 | PASS | היכולת לענות: האם אני מתחזק? איפה אני תקוע? כמה סטים לשריר? תדירות? שינוי במשקל והיקפים? … | D: DashboardTest, InsightEngineTest, VolumeAnalyticsTest, BodyAnalyticsTest, ProgressionEngineTest |

## ג. בדיקות

| קבוצה | הורצו | עברו | נכשלו | דולגו |
|---|---|---|---|---|
| `:domain` (JVM) | 287 | 287 | 0 | 0 |
| `:app` יחידה (Robolectric: Room, שירותים, התראות, מניפסט, Intents) | 119 | 119 | 0 | 0 |
| `:app` ממשק (Compose על Robolectric) | 40 | 39 | 0 | 1 |
| **סה"כ** | **446** | **445** | **0** | **1** |

**בדיקות שדולגו (1):** `PdfExportAttemptTest.a_long_hebrew_report_becomes_a_multi_page_pdf` - דילוג מכוון (assumption) כש-`PdfDocument` אינו זמין ב-Robolectric. אינו נספר כהצלחה; שורות ה-PDF במטריצה נשארות BLOCKED.

**מצב GitHub CI:** ריצה `38565145090` על Commit `e9aa61f773ea9c476d1f121d4221da70ebf6a2c3` - **success (כל השלבים, כולל Test gate)** (כולל שלב `Test gate` שמכשיל את הריצה על כל כשל בכל קבוצה). https://github.com/TomKruchenezki/LIFTOSWORKOUT-/actions/runs/38565145090

**בדיקות שלא ניתן היה להריץ (ולא סומנו כהצלחה):**

* Instrumentation tests על מכשיר/אמולטור - אין בסביבה. נדרש: אמולטור או מכשיר + `androidTest`.
* התנהגות ברקע / נעילה / Doze / חיסכון סוללה של Samsung, מסירת התראות, רטט - נדרש מכשיר.
* מצלמה, גלריה, בורר קבצים (Drive), Share Sheet של המערכת, ChatGPT/Claude כאפליקציות מקבלות - נדרש מכשיר עם האפליקציות.
* ציור PDF: ניסיון לצייר PDF אמיתי תחת Robolectric בגרפיקה native (`PdfExportAttemptTest`) **דולג** - `PdfDocument` אינו שמיש בסביבה הזו, ולכן ציור ה-PDF לא נבדק (נבדקת רק חלוקת העמודים ב-domain). בדיקה שדולגה אינה ראיה
* ביצועי UI, זיכרון וסוללה בפועל; TalkBack; בדיקת מראה חזותי (RTL, חפיפת טקסט, גרפים).
* Release (R8) בזמן ריצה; Floot מול קובץ אמיתי; Android Studio (הפרויקט נבנה ב-Gradle ב-CI).

**שלוש הקבוצות של היקף הבדיקות:** יחידה על JVM מוכיחה לוגיקה; Robolectric מוכיח שהמסכים והשירותים מתחברים לנתונים אמיתיים ולמנגנוני אנדרואיד; **אף אחד מהם אינו מוכיח שהממשק נראה ופועל נכון על הטלפון**.

### תרחישי E2E

| תרחיש | שלבים | PASS | PARTIAL |
|---|---|---|---|
| תרחיש A - אימון בחדר הכושר | 20 | 16 | 4 |
| תרחיש B - מעקב התקדמות | 7 | 7 | 0 |
| תרחיש C - מדדי גוף | 8 | 6 | 2 |
| תרחיש D - יומן ואירועים | 7 | 7 | 0 |
| תרחיש E - שחזור וגיבוי | 10 | 10 | 0 |

## ד. בעיות שנותרו (בלי הסתרה)

| מזהה | חומרה | בעיה | מצב |
|---|---|---|---|
| G7 | Low | מסך הבית ללא תובנות מקומיות (הן במסך ההתקדמות) | פתוח (שיפור עתידי, אינו דרישה) |
| OPEN-PDF | High | ייצוא PDF: ציור המסמך בפועל (PdfDocument) לא הורץ באף בדיקה | פתוח - BLOCKED, דורש מכשיר/אמולטור |
| OPEN-DEVICE | High | אין שום בדיקה על מכשיר או אמולטור: טיימר ברקע/נעילה, התראות, מצלמה/גלריה, בורר קבצים, Share Sheet, התקנה | פתוח - BLOCKED |
| OPEN-SIGN | Medium | ה-APK ה-release חתום במפתח ה-debug המשותף ולא במפתח הפצה | פתוח - דורש מפתח חתימה של הבעלים |
| OPEN-FLOOT | Medium | Importer של Floot נבדק רק מול מבנה שנבנה מתיאור המשתמש; לא סופק קובץ אמיתי | פתוח - ממתין לקובץ |
| OPEN-DRAG | Medium | גרירה לשינוי סדר (תוכנית) ו-Slider להשוואת תמונות לא נבדקו כמחווה | פתוח - BLOCKED |
| OPEN-SHEET | Low | בורר התרגילים (החלפה/הוספה) ושדות טקסט בתוך דיאלוגים/Bottom sheets לא נבדקים ב-UI | פתוח - הגבלת כלי בדיקה |
| OPEN-MASTER | Low | פרומפט המאסטר הארוך נחתך ב-20,000 תווים בתמלול; רק פרומפט 1 ופרומפט 2 נבדקו במלואם | הוצהר - לא נבדק מעבר לפרומפטים 1 ו-2 |

**פערים שנמצאו בביקורת ותוקנו** (פירוט ב-FINAL_REQUIREMENTS_AUDIT.md §8): G-AI-REPORT, G4, G3, G1, G2, G6, G5, T-MANIFEST, T-SWEEP, G-NOTIF.

**אין פער Critical פתוח.** פערי ה-High הפתוחים (`OPEN-PDF`, `OPEN-DEVICE`) חסומים בגלל הסביבה ולא בגלל קוד שניתן לתקן כאן.

## ה. מצב ה-APK

| שדה | Debug (לניסיון) | Release (R8) |
|---|---|---|
| קובץ | `app-debug.apk` | `app-release.apk` |
| מזהה חבילה | `app.liftos` | `app.liftos` |
| גרסה | `0.2.1` (versionCode 3) | `0.2.1` (versionCode 3) |
| `targetSdk` / `minSdk` | 36 / 26 | 36 / 26 |
| גודל | 22,590,907 בתים | 2,660,387 בתים |
| `SHA-256` | `60890d5287718d2daa6572cc402d00494aee96159b50cdee4ba985335d9b3cf2` | `ad6f761c16bc745f085a2b63b2221a260389150502644282fb248fba14b5a3a1` |
| חתימה | מפתח debug קבוע ומשותף (נבדק בלוג `apksigner`: חתום בסכמה v2 בלבד, `CN=LiftOS Debug, O=LiftOS, C=IL`, SHA-256 של האישור `33f6c500…ed32e`) | אותו מפתח debug (אין `keystore.properties`): אותו אישור `CN=LiftOS Debug`, אותו SHA-256 של האישור, סכמה v2 |
| תוצאת קומפילציה | `assembleDebug` ירוק | `assembleRelease` ירוק (R8) |
| Commit | `e9aa61f773ea9c476d1f121d4221da70ebf6a2c3` | `e9aa61f773ea9c476d1f121d4221da70ebf6a2c3` |
| זמן בנייה (UTC) | 2026-10-14 12:38:59 | 2026-10-14 12:38:59 |
| מיקום | [הריצה 38565145090](https://github.com/TomKruchenezki/LIFTOSWORKOUT-/actions/runs/38565145090) ← Artifacts ← `liftos-debug-apk` (ארטיפקט 11894466364) | אותו עמוד ← `liftos-release-apk` (ארטיפקט 11895650110) |

* **להתקנת ניסיון:** `liftos-debug-apk`.
* **חתום ומוכן להפצה:** **אין**. ה-Release חתום במפתח ה-debug המשותף ולכן ניתן להתקנה אישית בלבד; להפצה יש ליצור מפתח (ראה README.md, 'חתימת Release עם מפתח פרטי').
* ה-`SHA-256` נלקח מלוג ה-CI של הריצה (שלב 'APK facts'). הורדת הארטיפקטים מסביבת העבודה חסומה (מדיניות רשת: `productionresultssa*.blob.core.windows.net` מחזיר 403), ולכן לא חישבתי SHA-256 מקומי על הקובץ. ה-API של GitHub מדווח על `digest` של קובץ ה-ZIP: debug `sha256:f60d98de98f482cc4c6be68b7fe818c75675937945246e7a739f5859a473efe9`, release `sha256:111c7f8f70b380a3c8efcaff4c2e2ab5e838ffa99659ccc819a8878f4f26f94f`.
* ה-SHA-256 של ה-Release משתנה בין בניות של אותו קוד (חותמות זמן בחבילה); ה-Debug יציב בין בניות. השווה תמיד לערך של הריצה שממנה הורדת.
* **התיעוד נכתב אחרי ה-APK:** ה-Commit של קוד האפליקציה והבדיקות הוא `e9aa61f773ea9c476d1f121d4221da70ebf6a2c3` (ממנו נבנה ה-APK). ה-Commit(ים) שאחריו שינו קובצי `.md` בלבד (`[skip ci]`), ואפשר לאמת זאת: `git diff --stat e9aa61f773ea9c476d1f121d4221da70ebf6a2c3 HEAD`.

### הוראות התקנה ל-Samsung Galaxy S26 Ultra

1. בטלפון: GitHub ← `TomKruchenezki/LIFTOSWORKOUT-` ← **Actions** ← הריצה לפי המזהה למעלה ← **Artifacts** ← הורד `liftos-debug-apk` והוצא את `app-debug.apk` (ZIP).
2. פתח את הקובץ ואשר **התקנת אפליקציות לא מוכרות** לאפליקציה שפתחה אותו (הגדרות ← אבטחה ופרטיות).
3. אם Play Protect מזהיר על אפליקציה לא מוכרת - זו אזהרה צפויה לקובץ שלא הותקן מ-Play; בחר 'התקן בכל זאת'.
4. אשר הרשאת התראות בכניסה הראשונה. מומלץ: הגדרות ← אפליקציות ← LiftOS ← סוללה ← **ללא הגבלה**, כדי שטיימר המנוחה יעבוד בנעילה.
5. **לפני העדכון הראשון על נתונים אמיתיים - ייצא גיבוי** (הגדרות ← שמור גיבוי מלא).
6. אימות: `sha256sum app-debug.apk` ולהשוות לערך שבטבלה (אם הורדת מהריצה הזו).

**בדיקות ראשונות מומלצות על המכשיר** (כאן נמצאים הסיכונים שלא נבדקו): אימון מלא עם נעילת מסך באמצע מנוחה; התראת סיום מנוחה; ייצוא PDF; צילום תמונה והשוואה; גיבוי ל-Drive ושחזור; פתיחת ChatGPT/Claude מהדוח.
