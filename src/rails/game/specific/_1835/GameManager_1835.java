package rails.game.specific._1835;

import rails.game.GameManager;
import rails.common.parser.GameOption;
import rails.game.Phase;
import rails.game.Player;
import rails.game.PublicCompany;
import rails.game.RailsRoot;
import rails.game.Round;


public class GameManager_1835 extends GameManager {

	private Round previousRound = null;
    private Player prFormStartingPlayer = null;

    public final static String M2_ID = "M2";
    public final static String PR_ID = "PR";
    public final static String OL_ID = "OL";
    public final static String MS_ID = "MS";
    public final static String WT_ID = "WT";
    public final static String HE_ID = "HE";
    public final static String BA_ID = "BA";
    public final static String SX_ID = "SX";
    public final static String BY_ID = "BY";
    
    public GameManager_1835(RailsRoot parent, String id) {
        super(parent, id);
    }
    
    /** In standard 1835, minors can run even if the start packet has not been completely sold,
     * unless the "MinorsRequireFloatedBY" option is in effect and the Bayerische
     * has not yet floated.
     * @return true only if minors can run.
     */
    @Override
    protected boolean runIfStartPacketIsNotCompletelySold() {
        if (getGameOption(GameOption.VARIANT).equalsIgnoreCase("Clemens")
                || getGameOption("MinorsRequireFloatedBY").equalsIgnoreCase("yes")) {
            return companyManager.getPublicCompany(GameManager_1835.BY_ID).hasFloated();
        }
        return true;
    }

    @Override
    public void nextRound(Round round) {

        if (round instanceof PrussianFormationRound) {
            if (interruptedRound != null) {
                setRound(interruptedRound);
                interruptedRound.resume();
                interruptedRound = null;
            } else if (previousRound != null) {
                super.nextRound(previousRound);
                previousRound = null;
            }
        } else {
        	Phase phase = getCurrentPhase();
            if ((phase.getName().equals("4") || phase.getName().equals("4+4")
                    || phase.getName().equals("5"))
                    && !PrussianFormationRound.prussianIsComplete(this)) {
                previousRound = round;
                startPrussianFormationRound (null);
            } else {
                super.nextRound(round);
            }
        }

    }

    public void startPrussianFormationRound(OperatingRound_1835 or) {
        interruptedRound = or;
        String roundName;
        if (interruptedRound == null) {
            // after a round
            roundName = "PrussianFormationRound_after_" + previousRound.getId();
        } else {
            roundName = "PrussianFormationRound_in_" + or.getId();
        }
    	createRound(PrussianFormationRound.class, roundName).start();
    }

    public void setPrussianFormationStartingPlayer(Player prFormStartingPlayer) {
        this.prFormStartingPlayer = prFormStartingPlayer;
    }

    public Player getPrussianFormationStartingPlayer() {
        return prFormStartingPlayer;
    }

    @Override
    public int getPlayerCertificateLimit(Player player) {
        int limit = playerCertificateLimit.value();
        for (PublicCompany company : companyManager.getAllPublicCompanies()) {
            if (company.getType().getId().equalsIgnoreCase("Major")
                    && company.getPresident() == player
                    && player.getPortfolioModel().getShare(company) >= 80) limit++;
        }
        return limit;
    }

}
