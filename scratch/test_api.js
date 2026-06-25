async function run() {
  try {
    console.log('Attempting login on UAT domain...');
    const loginRes = await fetch('https://api-uat.laritraders.store/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        phone: '7084285785',
        password: 'admin123'
      })
    });
    
    const loginData = await loginRes.json();
    console.log('Login Response:', loginData);
    
    const token = loginData.token || loginData.data?.token;
    if (!token) {
      console.log('No token found in response, maybe MFA is required?');
      return;
    }
    
    console.log('Token obtained. Fetching bills from UAT domain...');
    const billsRes = await fetch('https://api-uat.laritraders.store/api/bills', {
      method: 'GET',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
    
    console.log('Bills Response Status:', billsRes.status);
    const billsData = await billsRes.json();
    console.log('Bills Response Data:', JSON.stringify(billsData, null, 2));
  } catch (err) {
    console.error('Error occurred:', err.message);
  }
}

run();
