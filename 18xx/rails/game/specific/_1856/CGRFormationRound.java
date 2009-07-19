package rails.game.specific._1856;

import java.util.*;

import rails.game.*;
import rails.game.action.*;
import rails.game.move.CashMove;
import rails.game.move.MoveSet;
import rails.game.state.IntegerState;
import rails.util.LocalText;


public class CGRFormationRound extends SwitchableUIRound {

    private final String CGRNAME = "CGR";

    private Player startingPlayer;
    private Map<Player, List<PublicCompanyI>> companiesToRepayLoans = null;
    private PublicCompanyI currentCompany = null;
    private int maxLoansToRepayByPresident = 0;
    private List<PublicCompanyI> mergingCompanies = new ArrayList<PublicCompanyI>();
    private PublicCompanyI cgr = gameManager.getCompanyManager().getCompanyByName(CGRNAME);
    private String cgrName = CGRNAME;
    private List<TrainI> trainsToDiscardFrom = null;
    private boolean forcedTrainDiscard = true;
    private List<ExchangeableToken> tokensToExchangeFrom = null;
    private List<BaseToken> nonHomeTokens = null;
    
    private IntegerState stepObject = new IntegerState ("CGRFormStep", 0);

    public static final int STEP_REPAY_LOANS = 1;
    public static final int STEP_DISCARD_TRAINS = 2;
    public static final int STEP_EXCHANGE_TOKENS = 3;

    public CGRFormationRound (GameManagerI gameManager) {
        super (gameManager);
    }

    @Override
    /** This class needs the game status window to show up
     * rather than the operating round window.
     */
    public Class<? extends RoundI> getRoundTypeForUI () {
        return StockRound.class;
        //return OperatingRound.class;
    }

    public void start (Player startingPlayer) {

        this.startingPlayer = startingPlayer;

        Player president;

        companiesToRepayLoans = null;

        ReportBuffer.add(LocalText.getText("StartCGRFormationRound",
                startingPlayer.getName()));

        // Collect companies having loans
        for (PublicCompanyI company : getOperatingCompanies()) {
            if (company.getCurrentNumberOfLoans() > 0) {
                if (companiesToRepayLoans == null) {
                    companiesToRepayLoans
                        = new HashMap<Player, List<PublicCompanyI>>();
                }
                president = company.getPresident();
                if (!companiesToRepayLoans.containsKey(president)) {
                    companiesToRepayLoans.put (president, new ArrayList<PublicCompanyI>());
                }
                companiesToRepayLoans.get(president).add(company);
            }
        }
        
        if (companiesToRepayLoans == null) {
            finishRound();
            return;
        }

        setStep(STEP_REPAY_LOANS);

        setCurrentPlayer (startingPlayer);

        process (null);
    }
    
    private void setStep(int step) {
        stepObject.set(step);
    }
    
    private int getStep() {
        return stepObject.intValue();
    }

    private boolean setNextCompanyNeedingPresidentIntervention () {

        while (true) {

            while (!companiesToRepayLoans.containsKey(getCurrentPlayer())) {
                gameManager.setNextPlayer();
                if (getCurrentPlayer().equals(startingPlayer)) {
                    return false;
                }
            }

            // Player to act already has been selected
            Player player = getCurrentPlayer();
            if (companiesToRepayLoans.get(player).isEmpty()) {
                companiesToRepayLoans.remove(player);
                continue;
            }
            currentCompany = companiesToRepayLoans.get(player).get(0);
            companiesToRepayLoans.get(player).remove(currentCompany);

            int numberOfLoans = currentCompany.getCurrentNumberOfLoans();
            if (numberOfLoans == 0) continue;

            int compCash = currentCompany.getCash();
            int presCash = player.getCash();
            int valuePerLoan = currentCompany.getValuePerLoan();
            String message;
            int payment;

            message = LocalText.getText("CompanyHasLoans",
                    currentCompany.getName(),
                    player.getName(),
                    numberOfLoans,
                    Bank.format(valuePerLoan),
                    Bank.format(numberOfLoans * valuePerLoan));
            ReportBuffer.add(message);
            DisplayBuffer.add(message, false);

            // Let company repay all loans for which it has the cash
            int numberToRepay = Math.min(numberOfLoans,
                    compCash / valuePerLoan);
            if (numberToRepay > 0) {
                payment = numberToRepay * valuePerLoan;

                new CashMove (currentCompany, null, payment);
                currentCompany.addLoans(-numberToRepay);

                message = LocalText.getText("CompanyRepaysLoans",
                        currentCompany.getName(),
                        Bank.format(payment),
                        Bank.format(numberOfLoans * valuePerLoan),
                        numberToRepay,
                        Bank.format(valuePerLoan));
                ReportBuffer.add (message);
                DisplayBuffer.add(message, false);
            }

            // If that was all, we're done with this company
            numberOfLoans = currentCompany.getCurrentNumberOfLoans();
            if (numberOfLoans == 0) continue;

            // Check the president's cash
            // He should be involved if at least one extra loan could be repaid
            compCash = currentCompany.getCash();
            if ((compCash + presCash) / valuePerLoan > 0) {
                int maxNumber = Math.min((compCash + presCash)/valuePerLoan, numberOfLoans);
                if (maxNumber == numberOfLoans) {
                    DisplayBuffer.add(LocalText.getText("YouCanRepayAllLoans",
                            player.getName(),
                            maxNumber,
                            currentCompany.getName()),
                        false);
                } else {
                    DisplayBuffer.add(LocalText.getText("YouCannotRepayAllLoans",
                            player.getName(),
                            maxNumber,
                            numberOfLoans,
                            currentCompany.getName()),
                        false);
                }
                maxLoansToRepayByPresident = maxNumber;
                break;
            } else {
                // President cannot help, this company will merge into CGR anyway
                mergingCompanies.add(currentCompany);
                message = LocalText.getText("WillMergeInto",
                        currentCompany.getName(),
                        "CGR");
                DisplayBuffer.add(message, false);
                ReportBuffer.add(message);
                continue;
            }
        }
        return true;
    }

    @Override
    public boolean setPossibleActions() {

        int step = getStep();
        if (step == STEP_REPAY_LOANS) {
            RepayLoans action = new RepayLoans (currentCompany, 0,
                    maxLoansToRepayByPresident,
                    currentCompany.getValuePerLoan());
            possibleActions.add(action);
            roundTypeForUI = StockRound_1856.class;
        } else if (step == STEP_EXCHANGE_TOKENS) {
            int numberToExchange = cgr.getNumberOfFreeBaseTokens();
            ExchangeTokens action = new ExchangeTokens (tokensToExchangeFrom,
                    numberToExchange, numberToExchange);
            action.setCompany(cgr);
            possibleActions.add(action);
            roundTypeForUI = OperatingRound_1856.class;
        } else if (step == STEP_DISCARD_TRAINS) {
            DiscardTrain action = new DiscardTrain (cgr,
                    trainsToDiscardFrom, forcedTrainDiscard);
            possibleActions.add(action);
            roundTypeForUI = OperatingRound_1856.class;
       }
        return true;

    }

    protected boolean repayLoans (RepayLoans action) {
        
        // TODO Validation skipped for now...

        MoveSet.start(true);
        
        PublicCompanyI company = action.getCompany();
        int numberRepaid = action.getNumberRepaid();
        int repayment = numberRepaid * company.getValuePerLoan();
        
        if (repayment > 0) {
        
            int repaymentByCompany = Math.min (repayment, company.getCash());
            int repaymentByPresident = repayment - repaymentByCompany;
    
            company.addLoans(-numberRepaid);
            if (repaymentByCompany > 0) {
                new CashMove (company, null, repaymentByCompany);
                ReportBuffer.add (LocalText.getText("CompanyRepaysLoans",
                        company.getName(),
                    Bank.format(repaymentByCompany),
                    Bank.format(repayment),
                    numberRepaid,
                    Bank.format(company.getValuePerLoan())));
            }
            if (repaymentByPresident > 0) {
                Player president = company.getPresident();
                new CashMove (president, null, repaymentByPresident);
                ReportBuffer.add (LocalText.getText("CompanyRepaysLoansWithPresCash",
                        company.getName(),
                        Bank.format(repaymentByPresident),
                        Bank.format(repayment),
                        numberRepaid,
                        Bank.format(company.getValuePerLoan()),
                        president.getName()));
            }
         }

         if (action.getCompany().getCurrentNumberOfLoans() > 0) {
            mergingCompanies.add(currentCompany);
            String message = LocalText.getText("WillMergeInto",
                    currentCompany.getName(),
                    "CGR");
            DisplayBuffer.add(message, true);
            ReportBuffer.add(message);

        }

       return true;

    }

    private void formCGR () {

        Player player;
        Portfolio portfolio;
        int count, cgrSharesUsed, oldShares, newShares;
        PublicCertificateI cgrCert, poolCert;
        List<PublicCertificateI> certs = new ArrayList<PublicCertificateI>();
        Portfolio scrapHeap = Bank.getScrapHeap();
        Portfolio pool = Bank.getPool();
        Portfolio unavailable = Bank.getUnavailable();
        Portfolio ipo = Bank.getIpo();
        Player temporaryPresident = null;
        Player newPresident = null;
        Player firstCGRowner = null;
        int maxShares = 0;

        // Exchange the player shares
        setCurrentPlayer(startingPlayer);
        cgrSharesUsed = 0;

        do {
            player = getCurrentPlayer();
            portfolio = player.getPortfolio();
            oldShares = newShares = 0;
            certs.clear();
            poolCert = null;

            for (PublicCertificateI cert : player.getPortfolio().getCertificates()) {
                if (mergingCompanies.contains(cert.getCompany())) {
                    certs.add((cert));
                    oldShares++;
                    if (cert.isPresidentShare()) {
                        oldShares++;
                    }
                }
            }
            
            if (oldShares > 0) {
                
                count = oldShares;
                if (count >= 4 && temporaryPresident == null && cgrSharesUsed <= 18) {
                    cgrCert = cgr.getPresidentsShare();
                    cgrCert.moveTo(portfolio);
                    count -= 4;
                    cgrSharesUsed += 2;
                    newShares += 2;
                    temporaryPresident = player;
                }
                while (count >= 2 && cgrSharesUsed <= 19) {
                    cgrCert = unavailable.findCertificate(cgr, false);
                    cgrCert.moveTo(portfolio);
                    count -= 2;
                    cgrSharesUsed++;
                    newShares++;
                }
    
                String message = LocalText.getText("HasMergedShares",
                        player.getName(),
                        oldShares,
                        newShares,
                        "CGR");
                DisplayBuffer.add(message, false);
                ReportBuffer.add(message);
    
                if (count == 1) {
                    // Should work OK even if this is a president's share.
                    // In the pool we will treat all certs equally.
                    poolCert = certs.get(certs.size()-1);
                    poolCert.moveTo(pool);
                    certs.remove(poolCert);
    
                    message = LocalText.getText("HasPutShareInPool",
                            player.getName());
                    DisplayBuffer.add(message, false);
                    ReportBuffer.add(message);
    
                }
                // Note: old shares are removed when company is closed
    
                if (firstCGRowner == null) firstCGRowner = player;
    
                // Check for presidency
                if (newShares > maxShares) {
                    maxShares = newShares;
                    newPresident = player;
                }
            }

            gameManager.setNextPlayer();

        } while (getCurrentPlayer() != startingPlayer);

        // Exchange the pool shares
        certs.clear();
        oldShares = newShares = 0;

        for (PublicCertificateI cert : pool.getCertificates()) {
            if (mergingCompanies.contains(cert.getCompany())) {
                certs.add((cert));
                oldShares++;
            }
        }
        count = oldShares;
        while (count >= 2 && cgrSharesUsed <= 19) {
            cgrCert = unavailable.findCertificate(cgr, false);
            cgrCert.moveTo(pool);
            count -= 2;
            cgrSharesUsed++;
            newShares++;
        }

        String message = LocalText.getText("HasMergedShares",
                LocalText.getText("POOL"),
                oldShares,
                newShares,
                "CGR");
        DisplayBuffer.add(message);
        ReportBuffer.add(message);

        for (PublicCertificateI discardCert : certs) {
            discardCert.moveTo(scrapHeap);
        }

        log.info(cgrSharesUsed+" CGR shares are now in play");
        // Move the remaining CGR shares to the ipo.
        // Must clone the list first
        certs = new ArrayList<PublicCertificateI>(unavailable.getCertificatesPerCompany("CGR"));
        for (PublicCertificateI cert : certs) {
            cert.moveTo(ipo);
        }

        // Assign the new president
        if (temporaryPresident != newPresident) {
            temporaryPresident.getPortfolio().swapPresidentCertificate(cgr,
                    newPresident.getPortfolio());
        }

        newPresident.getPortfolio().getShareModel(cgr).setShare();
        message = LocalText.getText("IS_NOW_PRES_OF",
                newPresident.getName(), cgrName);
        ReportBuffer.add(message);
        DisplayBuffer.add(message);

        // Collect the old token spots, and move cash and trains
        List<BaseToken> homeTokens = new ArrayList<BaseToken>();
        nonHomeTokens = new ArrayList<BaseToken>();
        BaseToken bt;
        MapHex hex;
        City city;
        for (PublicCompanyI comp : mergingCompanies) {
            for (TokenI token :comp.getTokens()) {
                if (token instanceof BaseToken) {
                    bt = (BaseToken) token;
                    if (!bt.isPlaced()) continue;
                    city = (City) bt.getHolder();
                    hex = city.getHolder();
                    if (hex == comp.getHomeHex()) {
                        homeTokens.add(bt);
                    } else {
                        nonHomeTokens.add(bt);
                    }
                }
            }

            if (comp.getCash() > 0) {
                new CashMove (comp, cgr, comp.getCash());
            }
            List<TrainI> trains = new ArrayList<TrainI> (comp.getPortfolio().getTrainList());
            for (TrainI train : trains) {
                train.moveTo(cgr.getPortfolio());
            }
        }

        // Replace the home tokens
        for (BaseToken token : homeTokens) {
            city = (City) token.getHolder();
            hex = city.getHolder();
            token.moveTo(token.getCompany());
            if (hex.layBaseToken(cgr, city.getNumber())) {
                /* TODO: the false return value must be impossible. */
                ReportBuffer.add(LocalText.getText("ExchangesBaseToken",
                        cgrName, token.getCompany().getName(),
                        city.getName()));
                cgr.layBaseToken(hex, 0);
            }
        }

        // Clean up any non-home tokens on cities now having a CGR token
        for (BaseToken token : new ArrayList<BaseToken>(nonHomeTokens)) {
            city = (City) token.getHolder();
            hex = city.getHolder();
            List<BaseToken> otherTokens = hex.getBaseTokens();
            if (otherTokens != null) {
                for (BaseToken token2 : otherTokens) {
                    if (token2.getCompany() == cgr
                            || nonHomeTokens.contains(token2) && token2 != token) {
                        ReportBuffer.add(LocalText.getText("DiscardsBaseToken",
                                cgrName, token.getCompany().getName(),
                                city.getName()));
                        token.moveTo(token.getCompany());
                        nonHomeTokens.remove(token);
                        break;
                    }
                }
            }
        }

        // Prepare replacing the other tokens, if possible
        if (homeTokens.size() + nonHomeTokens.size() > cgr.getNumberOfBaseTokens()) {
            // CGR cannot replace all tokens, must choose
            // First collect old names per city
            Map<String, String> oldTokens = new HashMap<String, String>();
            String cityName;
            for (BaseToken token : nonHomeTokens) {
                if (token.getHolder() instanceof City) {
                    cityName = token.getHolder().getName();
                    if (oldTokens.containsKey(cityName)) {
                        oldTokens.put(cityName,
                                oldTokens.get(cityName)+","+token.getCompany().getName());
                    } else {
                        oldTokens.put(cityName, token.getCompany().getName());
                    }
                }
            }
            // Then create list of exchange spots. Sort it on hexname/city number
            tokensToExchangeFrom = new ArrayList<ExchangeableToken>();
            for (String key : new TreeSet<String> (oldTokens.keySet())) {
                 tokensToExchangeFrom.add(new ExchangeableToken(
                        key, oldTokens.get(key)));
        }
        } else {
            executeExchangeTokens (nonHomeTokens);
        }
        
        // Determine the CGR starting price, 
        // and close the absorbed companies.
        int lowestPrice = 999;
        int totalPrice = 0;
        int price;
        int numberMerged = mergingCompanies.size();
        for (PublicCompanyI comp : mergingCompanies) {
            price = comp.getMarketPrice();
            totalPrice += price;
            if (price < lowestPrice) lowestPrice = price;
            comp.setClosed();
        }
        if (numberMerged >= 3) {
            totalPrice -= lowestPrice;
            numberMerged--;
        }
        int cgrPrice = Math.max(100, ((int)((totalPrice/numberMerged)/5))*5);
        
        // Find the correct start space and start the CGR
        if (cgrPrice == 100) {
            cgr.start(100);
        } else {
            StockMarketI sm = StockMarket.getInstance();
            int prevColPrice = 100;
            int colPrice;
            StockSpaceI startSpace;
            for (int col=6; col <= sm.getNumberOfColumns(); col++) {
                colPrice = sm.getStockSpace(1, col).getPrice();
                if (cgrPrice > colPrice) continue;
                if (cgrPrice - prevColPrice < colPrice - cgrPrice) {
                    startSpace = sm.getStockSpace(1, col-1);
                } else {
                    startSpace = sm.getStockSpace(1, col);
                }
                cgr.start(startSpace);
                message = LocalText.getText("START_MERGED_COMPANY",
                        "CGR", 
                        Bank.format(startSpace.getPrice()), 
                        startSpace.getName());
                DisplayBuffer.add(message);
                ReportBuffer.add(message);
                break;
            }
        }
        cgr.setFloated();
        ReportBuffer.add (LocalText.getText("Floats", "CGR"));

        // Check the trains, autodiscard any excess non-permanent trains
        int trainLimit = cgr.getTrainLimit(gameManager.getCurrentPlayerIndex());
        List<TrainI> trains = cgr.getPortfolio().getTrainList();
outer:  while (cgr.getNumberOfTrains() > trainLimit) {
            for (TrainI train : trains) {
                if (!train.getType().isPermanent()) {
                    train.moveTo(pool);
                    ReportBuffer.add(LocalText.getText("CompanyDiscardsTrain",
                            cgrName, train.getName()));
                    continue outer;
                }
            }
            break;
        }
    }

   private void executeExchangeTokens (List<BaseToken> exchangedTokens) {
        City city;
        MapHex hex;
        for (BaseToken token : exchangedTokens) {
            // Remove old token
            city = (City) token.getHolder();
            hex = city.getHolder();
            token.moveTo(token.getCompany());
            // Replace it with a CGR token
            if (hex.layBaseToken(cgr, city.getNumber())) {
                cgr.layBaseToken(hex, 0);
                //log.debug("CGR exchanges a token at "+hex.getName()+" "+hex.getCityName());
            } else {
                log.error("Error in laying CGR token on "+hex.getName()+" "+hex.getCityName());
            }
        }
    }
    
    public boolean process (PossibleAction action) {
        
        boolean result = true;
        
        if (action instanceof RepayLoans) {
            result = repayLoans((RepayLoans)action);
        } else if (action instanceof DiscardTrain) {
            result = discardTrain((DiscardTrain)action);
        } else if (action instanceof ExchangeTokens) {
            result = exchangeTokens ((ExchangeTokens)action);
        }
        if (!result) return false;
        
        if (getStep() == STEP_REPAY_LOANS) {
            
            if (setNextCompanyNeedingPresidentIntervention()) {
                return true;
            }
            
            if (!mergingCompanies.isEmpty()) {
                formCGR();
                setStep (STEP_EXCHANGE_TOKENS);
            } else {
                //gameManager.nextRound(this);
                finishRound();
            }
        }

        if (getStep() == STEP_EXCHANGE_TOKENS) { 

            if (action instanceof ExchangeTokens) {
                tokensToExchangeFrom = null;
            } else if (tokensToExchangeFrom != null
                    && !tokensToExchangeFrom.isEmpty()) {
                return true;
            }
            setStep (STEP_DISCARD_TRAINS);
        }
        
        if (getStep() == STEP_DISCARD_TRAINS) {
 
            if (checkForTrainsToDiscard()) return true;
            //gameManager.nextRound(this);        
            finishRound();
        }

        return true;
    }
    
    
    private boolean checkForTrainsToDiscard () {

        // Check if CGR must discard trains
        if (cgr.getNumberOfTrains() > cgr.getCurrentTrainLimit()) {
            log.debug("CGR must discard trains");
            if (getStep() != STEP_DISCARD_TRAINS) {
                setStep(STEP_DISCARD_TRAINS);
            }
            trainsToDiscardFrom = cgr.getPortfolio().getTrainList();
            forcedTrainDiscard = true;
            return true;
        } else {
            // Check if CGR still has non-permanent trains
            // these may be discarded voluntarily
            trainsToDiscardFrom = new ArrayList<TrainI>();
            for (TrainI train : cgr.getPortfolio().getTrainList()) {
                if (!train.getType().isPermanent()) {
                    trainsToDiscardFrom.add(train);
               }
            }
            if (!trainsToDiscardFrom.isEmpty()) {
                if (getStep() != STEP_DISCARD_TRAINS) {
                    setStep(STEP_DISCARD_TRAINS);
                }
                forcedTrainDiscard = false;
                return true;
            }
        }
        return false;
    }

    public boolean discardTrain(DiscardTrain action) {

        TrainI train = action.getDiscardedTrain();
        PublicCompanyI company = action.getCompany();
        String companyName = company.getName();

        String errMsg = null;

        // Dummy loop to enable a quick jump out.
        while (true) {
            // Checks
            // Must be CGR
            if (company != cgr) {
                errMsg = LocalText.getText("WrongCompany",
                        company.getName(),
                        cgrName);
                break;
            }
            // Must be correct step
            if (getStep() != STEP_DISCARD_TRAINS) {
                errMsg = LocalText.getText("WrongActionNoDiscardTrain");
                break;
            }

            if (train == null && action.isForced()) {
                errMsg = LocalText.getText("NoTrainSpecified");
                break;
            }

            // Does the company own such a train?

            if (train != null && !company.getPortfolio().getTrainList().contains(train)) {
                errMsg =
                        LocalText.getText("CompanyDoesNotOwnTrain",
                                company.getName(),
                                train.getName() );
                break;
            }

            break;
        }
        if (errMsg != null) {
            DisplayBuffer.add(LocalText.getText("CannotDiscardTrain",
                    companyName,
                    train.getName(),
                    errMsg ));
            return false;
        }

        /* End of validation, start of execution */
        MoveSet.start(true);

        if (train != null) {

            if (action.isForced()) MoveSet.setLinkedToPrevious();

            train.moveTo(Bank.getPool());
            ReportBuffer.add(LocalText.getText("CompanyDiscardsTrain",
                    companyName,
                    train.getName() ));

        }

        return true;
    }

    @Override
    public String toString() {
        return "1856 CGRFormationRound";
    }


}
