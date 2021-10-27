/*
 * Backend - backend SQL file evaluation system class
 * 
 * Created - Paul J. Wagner, 18-Oct-2017
 * Last updated - PJW, 7-Oct-2019
 */
package sqlfe.general;

import java.io.File;
import java.io.IOException;
//import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import sqlfe.sqltests.ISQLTest;
import sqlfe.util.Utilities;

import static sqlfe.util.Utilities.threadSafeOutput;
//public class BackEnd implements Runnable {
public class BackEnd {
	// -- data
	private String dbmsName = null;							// name of DBMS to use (e.g. Oracle, MySQL)
	private String hostName = null;							// name or IP address of DBMS host (e.g. localhost, abc.univ.edu, 162.03.24.119)
	private String portString = null;						// port used on host (e.g. 3306 for MySQL)
	private String idName = null;							// system/schema id on DBMS host
	private String username = null;							// DBMS username
	private String password = null;							// DBMS password
	//private PrintStream printStream = null;					// printStream for console output
	
	private DecimalFormat decFormat = new DecimalFormat(); 
	private String mainFolderPath = null;					// main folder for other folders (submissions, evaluations) and assignment prop.			
	private String submissionFolderPath = null;			 	// location of submission files relative to workspace folder
	private String evaluationFolderPath = null; 			// location of evaluation output files relative to workspace folder
	private String assignmentPropertiesFileName = null;		// name of assignment properties file relative to workspace folder
	private String gradesFileName = null;					// name of grades summary file in evaluation folder
	
	private IDAO dao = null;								// data access object, created based on information from front end

	FrontEnd aFrontEnd = null;						// front end holding information from GUI to use in backend processing
	
	// -- constructor,default
	public BackEnd() {
		// nothing right now
	}
	
	// -- constructor, one arg
	public BackEnd(FrontEnd aFrontEnd) {
		this.aFrontEnd = aFrontEnd;
	}
	
	public void setFrontEnd(FrontEnd aFrontEnd) {
		this.aFrontEnd = aFrontEnd;
	}
	
	// -- process - general method to call other methods for processing
	public void process(FrontEnd aFrontEnd) {
		// move data in
		transferData(aFrontEnd);
		
		// set up and run the evaluation system in a separate thread to avoid linear flow in regard to GUI
		Thread thread = new Thread(() -> {
			evaluate();
		});
		thread.start();
	}	// end - method process
	
	// -- transferData() - move data from front end to back end, also set data based on these
	public void transferData(FrontEnd aFrontEnd) {
		dbmsName = aFrontEnd.getDbmsChoice();							// get DBMS choice directly from front end
		hostName = aFrontEnd.getDbmsHost();								// get DBMS host directly "
		portString = aFrontEnd.getDbmsPort();							// get DBMS port directly "
		idName = aFrontEnd.getDbmsSystemID();							// get DBMS system ID directly "
		username = aFrontEnd.getDbmsUsername();							// get DBMS username directly "
		password = aFrontEnd.getDbmsPassword();							// get DBMS password directly "
		mainFolderPath = Utilities.processSlashes(aFrontEnd.getEvaluationFolder());		// get main folder path directly "
		
		// set folder paths under main folder
		submissionFolderPath = mainFolderPath + "/files/"; 				// set location of submission files relative to workspace folder
		evaluationFolderPath = mainFolderPath + "/evaluations/"; 		// set location of evaluation output files relative to workspace folder
		assignmentPropertiesFileName = mainFolderPath + "/" + aFrontEnd.getAssignPropFile();	// get assignment properties file name directly "

		// make sure evaluations folder exists, and create it if not
		File directory = new File(String.valueOf(evaluationFolderPath));
		if (!directory.exists()) {										// if evaluations folder doesn't exist, create it
			directory.mkdir();
		} else {														// else clear out existing files
			for (File f: directory.listFiles()) 
				  f.delete();
		}
		
		// set grades summary file name in evaluations folder
		gradesFileName = evaluationFolderPath + "AAA_grade_summary.out";
		
		switch (dbmsName) {
		case "Oracle":
			dao = new OracleDataAccessObject(hostName, portString, idName, username, password, false);
			break;
		case "MySQL 5.x":
			dao = new MySQL5xDataAccessObject(hostName, portString, idName, username, password, false);
			break;
		case "MySQL 8.0":
			dao = new MySQL80DataAccessObject(hostName, portString, idName, username, password, false);
			break;
		case "Mock":
			dao = new MockDataAccessObject(hostName, portString, idName, username, password, false);
			break;
		default:
			System.err.println("Incorrect DAO specification");
		}
		
	}	// end - method transferData
	
	
	// -- evaluate() - do the main evaluation work
    private PrintWriter buildGradesWriter(Assignment a) {	//sets up grade writer for output file

        decFormat.setMaximumFractionDigits(2);	//sets decimal formatting to 2 decimal places
        PrintWriter gradesWriter = null;                        // grade summary file writer

        // set up the assignment output file
        try {
            gradesWriter = new PrintWriter(gradesFileName, "UTF-8");
            // output general information
            gradesWriter.println("Assignment  : " + a.getAssignmentName());
            gradesWriter.println("");
        } catch (IOException ioe) {
            System.err.println("IOException in writing to file " + gradesFileName);
        }
        return gradesWriter;
    } // end - buildGradesWriter

    private List<Question> getCurrentQuestions(List<Question> questions, QuestionAnswer questionAnswer) {
        // find the matching question(submission) for the answer
        List<Question> currQuestions = new ArrayList<Question>();
        boolean foundOne = false;
        boolean foundAll = false;
        int questionIndex = 0;
        while (questionIndex < questions.size() && !foundAll) {
            int indexOfQNum = questions.get(questionIndex).getQNumStr().indexOf(questionAnswer.getQNumStr());
            // first match
            if (!foundOne && indexOfQNum == 0) {
                foundOne = true;
                currQuestions.add(questions.get(questionIndex));        // use this question
            }
            // subsequent match
            else if (foundOne && indexOfQNum == 0) {
                currQuestions.add(questions.get(questionIndex));        // add this question too
            }
            // first non-match after subsequent match
            else if (foundOne) {
                foundAll = true;
            }
            // not a match
            questionIndex++;
        }    // end - while looking for question(submission) to match student answer

        if (!foundOne) {
            System.err.println("cannot find question");
        }
        return currQuestions;
    } // end - getCurrentQuestions

    private void evaluateTests(List<ISQLTest> questionTests, List<Integer> questionPercentages, List<String> questionConditions, List<EvalComponentInQuestion> questionEvalComps) {
    	// creates test object containing results of evaluation
    	for (EvalComponentInQuestion questionEvalComp : questionEvalComps) {
            // get test names
            String currTestName = "sqlfe.sqltests." + questionEvalComp.getEvalComponentName();

            // make test object out of test name
            questionTests.add(makeTestObject(currTestName));

            // get current percents
            questionPercentages.add(questionEvalComp.getPercent());

            // get current condition
            questionConditions.add(questionEvalComp.getCondition());
        }    // end - for each test in question
    } // end - evaluateTests

    private ISQLTest makeTestObject(String currTestName) { 	//creates test objects for evaluation
        try {
            Class<?> aClass = Class.forName(currTestName);
            Object oTest = aClass.newInstance();
            return (ISQLTest) oTest;
        } catch (Exception e) {	//error handling
            System.out.println("exception in generating class object from name");
        }
        return null;
    } // end - makeTestObjects

    private void processSubmission(Submission submission, List<Question> questions, PrintWriter gradesWriter) {
    	// parses submission to grade accuracy of questions
        Utilities.threadSafeOutput("\nEvaluating " + submission.getSubmissionFileName() + ": \n    ");
        double submissionPoints = 0;
        ArrayList<QueryEvaluation> queryEvals = new ArrayList<QueryEvaluation>();

        // initialize output point string for grade summary file
        String outputPointString = ": ";

        // connect to data access object for each submission
        dao.connect();

        // process each question answer in the submission
        ArrayList<QuestionAnswer> questionAnswers = submission.getAnswers();
        if (questionAnswers != null) {
            for (QuestionAnswer questionAnswer : questionAnswers) {
                threadSafeOutput("Q" + questionAnswer.getQNumStr() + ".");

                Query actualQuery = questionAnswer.getActualQuery();

                List<Question> currQuestions = getCurrentQuestions(questions, questionAnswer);

                // loop through all possible questions, evaluate, choose max
                double highestPoints = -1.0;            // set below zero so any evaluation is better
                QueryEvaluation maxQE = null;		// variable for the highest query evaluation
                for (Question currQuestion : currQuestions) {
                    // get the desired query for this question
                    Query desiredQuery = currQuestion.getDesiredQuery();

                    // get the evaluation components for this question
                    List<EvalComponentInQuestion> questionEvalComps = currQuestion.getTests();
                    int maxPoints = currQuestion.getQuestionPoints();

                    ArrayList<ISQLTest> questionTests = new ArrayList<ISQLTest>();
                    ArrayList<Integer> questionPercentages = new ArrayList<Integer>();
                    ArrayList<String> questionConditions = new ArrayList<String>();

                    // evaluate all tests for this question
                    evaluateTests(questionTests, questionPercentages, questionConditions, questionEvalComps);

                    // build a query evaluation, evaluate and add this queryEvaluation to the current submission
                    QueryEvaluation queryEvaluation = new QueryEvaluation(actualQuery, desiredQuery, dao, maxPoints,
                            questionTests, questionPercentages, questionConditions, null, 0.0);
                    double questionPoints = queryEvaluation.evaluate();

                    // use maximum score if multiple options for question
                    if (questionPoints > highestPoints) {
                        highestPoints = questionPoints;
                        maxQE = queryEvaluation;
                    }
                }
                // end - for each question
                queryEvals.add(maxQE);                    // add best queryEvaluation for this answer to the list

                submissionPoints += highestPoints;        // add the highest question score to the submission total

                outputPointString += (decFormat.format(highestPoints) + ", ");    // add highest points to string for grade summary output
            }    // end - for each question answer //try other method

            submission.setTotalPoints(submissionPoints);                // add the total points to the submission
            submission.setQueryEvals(queryEvals);                    // add the query evaluations to the submission

            submission.writeSubmission(evaluationFolderPath);        // write out each submission'submission output file
        }    // end - if any question answers exist
        // clean up/disconnect data access object
        dao.disconnect();

        // write each total grade to grades file
        try {
            gradesWriter.println(submission.getStudentName() + ": " + decFormat.format(submission.getTotalPoints()) + outputPointString);
        } catch (Exception e) {
            System.err.println("Error in writing to grades file " + gradesFileName);
        }

    } // end - processSubmission

    public void evaluate() {
        // read in the assignment properties, getting the questions from 108 method
        Assignment newAssignment = new Assignment();
        newAssignment.readProperties(assignmentPropertiesFileName);
        ArrayList<Question> questions = newAssignment.getQuestions();

        // set up grade summary file

        PrintWriter gradesWriter = buildGradesWriter(newAssignment);

        // read in all submission files
        SubmissionCollection submissionCollection = new SubmissionCollection();
        submissionCollection.getAllFiles(submissionFolderPath, evaluationFolderPath, newAssignment.getAssignmentName());

        // process each submission in the collection
        submissionCollection.getSubmissions().forEach(submission -> processSubmission(submission, questions, gradesWriter));

        // close evaluation folder / grades file
        try {
            gradesWriter.close();
        } catch (Exception e) {
            System.err.println("Error in closing grades file: " + gradesFileName);
        } finally {
            gradesWriter.close();    //exception will reoccur and it will throw an exception again
        }

        // tell user that processing is done
        threadSafeOutput("\n\nProcessing of this submission set completed.\n");
    }    // end - method evaluate

}    // end - class BackEnd
