/*
 *                        ######
 *                        ######
 *  ############    ####( ######  #####. ######  ############   ############
 *  #############  #####( ######  #####. ######  #############  #############
 *         ######  #####( ######  #####. ######  #####  ######  #####  ######
 *  ###### ######  #####( ######  #####. ######  #####  #####   #####  ######
 *  ###### ######  #####( ######  #####. ######  #####          #####  ######
 *  #############  #############  #############  #############  #####  ######
 *   ############   ############  #############   ############  #####  ######
 *                                       ######
 *                                #############
 *                                ############
 *
 *  Adyen Hybris Extension
 *
 *  Copyright (c) 2017 Adyen B.V.
 *  This file is open source and available under the MIT license.
 *  See the LICENSE file for more info.
 */
package com.adyen.v6.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.log4j.Logger;
import com.adyen.model.FraudCheckResult;
import com.adyen.model.FraudResult;
import com.adyen.model.PaymentResult;
import de.hybris.platform.basecommerce.enums.FraudStatus;
import de.hybris.platform.core.model.order.OrderModel;
import de.hybris.platform.core.model.order.payment.PaymentInfoModel;
import de.hybris.platform.fraud.model.FraudReportModel;
import de.hybris.platform.fraud.model.FraudSymptomScoringModel;
import de.hybris.platform.servicelayer.model.ModelService;
import static com.adyen.v6.constants.Adyenv6coreConstants.PAYMENT_PROVIDER;

public class DefaultAdyenOrderService implements AdyenOrderService {
    private static final Logger LOG = Logger.getLogger(DefaultAdyenOrderService.class);
    private ModelService modelService;

    @Override
    public FraudReportModel createFraudReportFromPaymentResult(PaymentResult paymentResult) {
        FraudReportModel fraudReport = modelService.create(FraudReportModel.class);
        FraudResult fraudResult = paymentResult.getFraudResult();

        if (fraudResult == null) {
            LOG.debug("No fraud result found");
            return null;
        }

        fraudReport.setCode(paymentResult.getPspReference());
        fraudReport.setStatus(FraudStatus.OK);
        fraudReport.setExplanation("Score: " + fraudResult.getAccountScore());
        fraudReport.setTimestamp(new Date());
        fraudReport.setProvider(PAYMENT_PROVIDER);

        List<FraudSymptomScoringModel> fraudSymptomScorings = new ArrayList<>();
        for (FraudCheckResult fraudCheckResult : fraudResult.getFraudCheckResults()) {
            FraudSymptomScoringModel fraudSymptomScoring = modelService.create(FraudSymptomScoringModel.class);

            Integer score = fraudCheckResult.getAccountScore();
            if (score != null) {
                fraudSymptomScoring.setScore(score.doubleValue());
            } else {
                fraudSymptomScoring.setScore(0.0);
            }

            fraudSymptomScoring.setName(fraudCheckResult.getName());
            fraudSymptomScoring.setExplanation("Check id: " + fraudCheckResult.getCheckId());
            fraudSymptomScoring.setFraudReport(fraudReport);

            fraudSymptomScorings.add(fraudSymptomScoring);
        }

        fraudReport.setFraudSymptomScorings(fraudSymptomScorings);

        LOG.debug("Returning fraud report with score: " + fraudResult.getAccountScore());

        return fraudReport;
    }

    @Override
    public void storeFraudReport(FraudReportModel fraudReport) {
        List<FraudSymptomScoringModel> fraudSymptomScorings = fraudReport.getFraudSymptomScorings();
        modelService.save(fraudReport);

        for (FraudSymptomScoringModel fraudSymptomScoring : fraudSymptomScorings) {
            modelService.save(fraudSymptomScoring);
        }
    }

    @Override
    public void storeFraudReportFromPaymentResult(OrderModel order, PaymentResult paymentResult) {
        FraudReportModel fraudReport = createFraudReportFromPaymentResult(paymentResult);
        if(fraudReport != null) {
            fraudReport.setOrder(order);
            storeFraudReport(fraudReport);
        }
    }

    @Override
    public void updateOrderFromPaymentResult(OrderModel order, PaymentResult paymentResult) {
        if (order == null) {
            LOG.error("Order is null");
            return;
        }

        PaymentInfoModel paymentInfo = order.getPaymentInfo();

        paymentInfo.setAdyenPaymentMethod(paymentResult.getPaymentMethod());
        paymentInfo.setAdyenAuthCode(paymentResult.getAuthCode());
        paymentInfo.setAdyenAvsResult(paymentResult.getAvsResult());
        paymentInfo.setAdyenCardBin(paymentResult.getCardBin());
        paymentInfo.setAdyenCardHolder(paymentResult.getCardHolderName());
        paymentInfo.setAdyenCardSummary(paymentResult.getCardSummary());
        paymentInfo.setAdyenCardExpiry(paymentResult.getExpiryDate());
        paymentInfo.setAdyenThreeDOffered(paymentResult.get3DOffered());
        paymentInfo.setAdyenThreeDAuthenticated(paymentResult.get3DAuthenticated());

        modelService.save(paymentInfo);

        storeFraudReportFromPaymentResult(order, paymentResult);
    }

    public ModelService getModelService() {
        return modelService;
    }

    public void setModelService(ModelService modelService) {
        this.modelService = modelService;
    }
}
