async function run() {
  try {
    const loginRes = await fetch('https://api-uat.laritraders.store/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ phone: '7084285785', password: 'admin123' })
    });
    const loginData = await loginRes.json();
    const token = loginData.token || loginData.data?.token;

    const res1 = await fetch('https://api-uat.laritraders.store/api/bills?page=0&size=20&sort=createdAt,desc', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const data1 = await res1.json();
    console.log('With params, isArray:', Array.isArray(data1.data));
    console.log('With params, keys of data:', data1.data ? Object.keys(data1.data) : 'null');

    const res2 = await fetch('https://api-uat.laritraders.store/api/bills', {
      headers: { 'Authorization': `Bearer ${token}` }
    });
    const data2 = await res2.json();
    console.log('Without params, isArray:', Array.isArray(data2.data));
  } catch (err) {
    console.error(err);
  }
}
run();
