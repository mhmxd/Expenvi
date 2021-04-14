package envi.experiment;

import com.google.common.collect.ImmutableList;
import envi.Utils;
import envi.gui.Circle;
import envi.Config;
import envi.gui.ExperimentPanel;
import envi.gui.MainFrame;
import io.reactivex.rxjava3.subjects.PublishSubject;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.List;

public class Experimenter {

    private static Experimenter self = null; // for singleton

    // Vars
    private List<Integer> radList   = new ArrayList<>();
    private List<Integer> distList  = new ArrayList<>();
    private List<Integer> dirList   = ImmutableList.of(0, 1); // 0: Left | 1: Right
    private List<List<Integer>> expVarList = new ArrayList<>();

    // Experiment
    private int participID = 1; // Participant's ID TODO: Convert to String?
    private int currExpNum = 1; // Currently always 1 (for each participant)

    // Blocks
    private List<Block> blocks = new ArrayList<>();
    private int currBlockInd = 0;

    // Trials
    private int currTrialNum;

    // General vars
    private int minX, maxX, minY, maxY;

    // For publishing the state of the experiment
    private PublishSubject<String> expSubject;

    /**
     * Get the instance
     * @return the singleton instance
     */
    public static Experimenter get() {
        if (self == null) self = new Experimenter();
        return self;
    }

    /**
     * Constructor
     */
    private Experimenter() {
        expSubject = PublishSubject.create();

        // Save radii and distances in px values
        for(int rad: Config.targetRadiiMM) {
            radList.add(Utils.mm2px(rad));
        }
        System.out.println(radList);
        for(int dist: Config.distancesMM) {
            distList.add(Utils.mm2px(dist));
        }
        System.out.println(distList);
    }

    /**
     * Get the PublishSubject to subscribe to
     * @return exPublishSubject
     */
    public PublishSubject<String> getExpSubject() {
        expSubject.subscribe(s -> System.out.println(s));
        return expSubject;
    }

    /***
     * Start the experiment
     */
    public void startExperiment() {
        System.out.println("Experiment started");

        // participant starts
        Mologger.get().logParticipStart(participID);

        // Log the start of the experiment
        Mologger.get().logExpStart(participID, currExpNum, LocalDateTime.now());

        // Generate the combinations rad/dist/dir
        generateVarList();

        // Get the window size
        Rectangle windowSize = MainFrame.getFrame().getBounds();

        // Generate blocks
        for (int bi = 0; bi < Config.N_BLOCKS_IN_EXPERIMENT; bi++) {
            blocks.add(new Block(TRIAL_TYPE.FITTS)
                            .setupFittsTrials(expVarList, windowSize.width, windowSize.height));
        }
//        System.out.println("- Blocks created");

        // Publish the start of the experiment
        expSubject.onNext(Utils.MSSG_BEG_EXP + "_" + currExpNum);

        // Run the first block
        currBlockInd = 0;
        startBlock(currBlockInd);
    }

    /**
     * Start a block
     * @param blkInd Block index
     */
    private void startBlock(int blkInd) {
        int blkNum = currBlockInd + 1;

        // Log the block start
        Mologger.get().logBlockStart(
                participID,
                currExpNum,
                blkNum,
                LocalTime.now());

        // Publish
        expSubject.onNext(Utils.MSSG_BEG_BLK + "_" + blkNum);

        // Start the block
        currTrialNum = 1;
        blocks.get(blkInd).setCurrTrialInd(-1); // TODO: is it needed?
        FittsTrial ftr = blocks.get(blkInd).getNextTrial(true);
        if (ftr != null) runFittsTrial(ftr);
        else System.out.println("Problem in loading the trials! Block #" + blkInd);
    }

    /**
     * Trial is done
     * @param wasSuccess Was it successful?
     */
    public void trialDone(boolean wasSuccess) {
        // Go to the next trial
        FittsTrial ftr = blocks.get(currBlockInd).getNextTrial(wasSuccess);
        if (ftr != null) {
            System.out.println("Next trial");
            // Log the end of the current trial
            Mologger.get().logTrialEnd();

            // Publish
            expSubject.onNext(Utils.MSSG_END_TRL);

            // Run the next trial
            currTrialNum++;
            runFittsTrial(ftr);
        }
        else { // Trials in the block finished
            System.out.println("Trials finished");
            // Log the end of the block
            Mologger.get().logBlockEnd(
                    participID,
                    currExpNum,
                    currBlockInd + 1,
                    LocalTime.now());

            // Publish
            expSubject.onNext(Utils.MSSG_END_BLK);

            // Show the break dialog
            showBreak();
        }
    }

    /**
     * Run a Fitts trial
     * @param ftr Fitts trial
     */
    private void runFittsTrial(FittsTrial ftr) {

        // Create circles
        Circle stacle = new Circle(ftr.getStaclePosition(), Config.STACLE_RAD_MM);
        Circle tarcle = new Circle(ftr.getTarclePosition(), ftr.getTarRad());

        // Create and send the panel to be drawn
        ExperimentPanel expPanel = new ExperimentPanel();
        expPanel.setCircles(stacle, tarcle);
        String trlStat = "Trial: " + currTrialNum;
        int blockNum = currBlockInd + 1;
        String blkStat = "Block: " + blockNum + " / " + blocks.size();
        expPanel.setStatTexts(blkStat, trlStat);
        MainFrame.getFrame().drawPanel(expPanel);
    }

    /**
     * Show a break (between blocks)
     */
    public void showBreak() {
        expSubject.onNext(Utils.MSSG_END_LOG);

        int input = JOptionPane.showOptionDialog(
                MainFrame.getFrame(),
                Utils.DIMSSG_BLOCK_FINISH,
                "BLOCK FINISHED",
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                null,
                null);
        if(input == JOptionPane.OK_OPTION) //TODO: Change to "Continue"
        {
            if (currBlockInd + 1 == blocks.size()) { // Blocks finished
                System.out.println("All blocks finished");
                System.exit(0);
            } else { // Continue to the next block
                currBlockInd++;
                startBlock(currBlockInd);
            }
        }
    }


    /**
     * Generate all the combinations of radii, distances, directions
     */
    private void generateVarList() {
        // Generate all the pairs of radius/distance (using Point for int,int)
        for (int rad: radList) {
            for (int dist: distList) {
                for (int dir: dirList) {
                    expVarList.add(ImmutableList.of(rad, dist, dir));
                }
            }
        }
    }

    /**
     * Get the participant's ID
     * @return Participant's ID
     */
    public int getPID() {
        return participID;
    }

}