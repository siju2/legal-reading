(() => {
  const button = document.getElementById('checkout');
  if (!button) return;
  const message = document.getElementById('payment-message');
  const form = document.getElementById('verify-payment');
  button.addEventListener('click', async () => {
    button.disabled = true;
    message.textContent = '결제 요청을 준비하고 있습니다.';
    try {
      if (!window.PortOne) throw new Error('결제창을 불러오지 못했습니다. 페이지를 새로 열어 주세요.');
      const csrf = form.querySelector('input[name="_csrf"]');
      const response = await fetch('/billing/orders', {method: 'POST', headers: {'X-CSRF-TOKEN': csrf.value, 'Accept': 'application/json'}});
      if (response.redirected) throw new Error('로그인 시간이 만료되었습니다. 다시 로그인해 주세요.');
      const order = await response.json();
      if (!response.ok) throw new Error(order.message || '결제 요청을 준비하지 못했습니다.');
      const result = await PortOne.requestPayment({
        storeId: order.storeId, channelKey: order.channelKey, paymentId: order.paymentId,
        orderName: order.orderName, totalAmount: order.totalAmount, currency: order.currency,
        payMethod: 'CARD', redirectUrl: `${location.origin}/billing/return`, forceRedirect: true
      });
      if (!result) throw new Error('결제창이 닫혔습니다. 결제 내역에서 상태를 확인해 주세요.');
      if (result.code) throw new Error(result.message || '결제가 완료되지 않았습니다.');
      document.getElementById('verification-id').value = order.paymentId;
      form.submit();
    } catch (error) { message.textContent = error.message || '결제 요청에 실패했습니다. 결제 내역을 확인해 주세요.'; }
    finally { button.disabled = false; }
  });
})();
