const stripe = Stripe('pk_test_51TQRHdCPuMjWnUiTVlRVI8VfC1jsWAfDe0CommZ6zk5KR82Us9bidTWe7VNWdBZLADGGfeeYtnLEBm5MPn0Vayta00Ur3mzWEU');
const paymentButton = document.querySelector('#paymentButton');

paymentButton.addEventListener('click', () => {
 stripe.redirectToCheckout({
   sessionId: sessionId
 })
});