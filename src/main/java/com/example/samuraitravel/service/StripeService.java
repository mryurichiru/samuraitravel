package com.example.samuraitravel.service;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.samuraitravel.form.ReservationRegisterForm;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionRetrieveParams;


@Service
public class StripeService {
	@Value("${stripe.api-key}")
	private String stripeApiKey;
	
	 private final ReservationService reservationService;
	    
	    public StripeService(ReservationService reservationService) {
	        this.reservationService = reservationService;
	    }	
	        
	   	 // セッションを作成し、Stripeに必要な情報を返す	
	public String createStripeSession(String houseName, ReservationRegisterForm reservationRegisterForm, HttpServletRequest httpServletRequest) {
		Stripe.apiKey = stripeApiKey;

		String requestUrl = new String(httpServletRequest.getRequestURL());
		SessionCreateParams params =
				SessionCreateParams.builder()/** ロガー */
				.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
				.addLineItem(
	                    SessionCreateParams.LineItem.builder()
	                        .setPriceData(
	                            SessionCreateParams.LineItem.PriceData.builder()   
	                                .setProductData(
	                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
	                                        .setName(houseName)
	                                        .build())
	                                .setUnitAmount((long)reservationRegisterForm.getAmount())
	                                .setCurrency("jpy")                                
	                                .build())
	                        .setQuantity(1L)
	                        .build())
.setMode(SessionCreateParams.Mode.PAYMENT)
.setSuccessUrl(requestUrl.replaceAll("/houses/[0-9]+/reservations/confirm", "") + "/reservations?reserved")
.setCancelUrl(requestUrl.replace("/reservations/confirm", ""))
.setPaymentIntentData(
    SessionCreateParams.PaymentIntentData.builder()
        .putMetadata("houseId", reservationRegisterForm.getHouseId().toString())
        .putMetadata("userId", reservationRegisterForm.getUserId().toString())
        .putMetadata("checkinDate", reservationRegisterForm.getCheckinDate())
        .putMetadata("checkoutDate", reservationRegisterForm.getCheckoutDate())
        .putMetadata("numberOfPeople", reservationRegisterForm.getNumberOfPeople().toString())
        .putMetadata("amount", reservationRegisterForm.getAmount().toString())
        .build())
.build();
try {
Session session = Session.create(params);
return session.getId();
} catch (StripeException e) {
e.printStackTrace();
return "";
}
} 
	// セッションから予約情報を取得し、ReservationServiceクラスを介してデータベースに登録する  
	//ここから書き換えてみたVer
    	public void processSessionCompleted(Event event) {

    	    // ① イベントタイプをチェック（超重要）
    	    if (!"checkout.session.completed".equals(event.getType())) {
    	        System.out.println("対象外イベント: " + event.getType());
    	        return;
    	    }

    	    var deserializer = event.getDataObjectDeserializer();

    	    // ② デシリアライズ確認
    	    if (deserializer.getObject().isPresent()) {

    	        Session session = (Session) deserializer.getObject().get();

    	        try {
    	            SessionRetrieveParams params = SessionRetrieveParams.builder()
    	                    .addExpand("payment_intent")
    	                    .build();

    	            session = Session.retrieve(session.getId(), params, null);

    	            // ③ metadata取得（ここが超重要）
    	            Map<String, String> metadata = session.getPaymentIntentObject().getMetadata();

    	            // デバッグ用（必ず一度確認）
    	            System.out.println("metadata: " + metadata);

    	            // ④ DB保存
    	            reservationService.create(metadata);

    	            System.out.println("予約登録 成功");

    	        } catch (Exception e) {
    	            System.out.println("予約登録 失敗");
    	            e.printStackTrace(); // ← これが本当の原因を出す
    	        }

    	    } else {
    	        // ⑤ デシリアライズ失敗時（これが今回の本命）
    	        System.out.println("⚠ StripeObjectの変換に失敗");
    	        System.out.println("Raw JSON: " + deserializer.getRawJson());
    	    }

    	    System.out.println("Stripe API Version: " + event.getApiVersion());
    	    System.out.println("stripe-java Version: " + Stripe.VERSION);
    	}	
    	
    	
    	/* 
    	     public void processSessionCompleted(Event event) {
    	     StripeObject stripeObject = event.getDataObjectDeserializer()
    		    .getObject()
    		    .orElseThrow(() -> new RuntimeException("StripeObjectが取得できません"));
    		     Optional<StripeObject> optionalStripeObject = event.getDataObjectDeserializer().getObject();
        optionalStripeObject.ifPresentOrElse(stripeObject -> {
            Session session = (Session)stripeObject;
            SessionRetrieveParams params = SessionRetrieveParams.builder().addExpand("payment_intent").build();

    	
            try {
                session = Session.retrieve(session.getId(), params, null);
                Map<String, String> paymentIntentObject = session.getPaymentIntentObject().getMetadata();
                reservationService.create(paymentIntentObject);
            } catch (StripeException e) {
                e.printStackTrace();
            }
            System.out.println("予約一覧ページの登録処理が成功しました。");
            System.out.println("Stripe API Version: " + event.getApiVersion());
            System.out.println("stripe-java Version: " + Stripe.VERSION);
        },
        () -> {
            System.out.println("予約一覧ページの登録処理が失敗しました。");
            System.out.println("Stripe API Version: " + event.getApiVersion());
            System.out.println("stripe-java Version: " + Stripe.VERSION);
        });
    }
    */
    	
	}
