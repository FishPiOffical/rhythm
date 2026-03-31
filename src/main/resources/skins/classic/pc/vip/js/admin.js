let memberships = [];
let coupon = [];
// 初始化页面
document.addEventListener('DOMContentLoaded', function () {
    // 绑定事件
    bindEvents();
    // 加载数据
    loadCoupons();
    loadMembershipLevels();
});

// 绑定事件
function bindEvents() {
    // Tab切换
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', function () {
            const tabId = this.getAttribute('data-tab');
            // 更新tab状态
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            this.classList.add('active');

            // 更新内容显示
            document.querySelectorAll('.tab-content').forEach(content => {
                content.classList.remove('active');
            });
            document.getElementById(tabId).classList.add('active');
        });
    });

    // 表单提交
    document.getElementById('membershipForm').addEventListener('submit', function (e) {
        e.preventDefault();
        addMembership();
    });

    document.getElementById('couponForm').addEventListener('submit', function (e) {
        e.preventDefault();
        addCoupon();
    });
}

// 会员月卡相关函数
function addMembership() {
    const formData = {
        lvName: document.getElementById('lvName').value.trim(),
        lvCode: document.getElementById('lvCode').value.trim(),
        price: parseFloat(document.getElementById('price').value),
        durationType: document.getElementById('durationType').value.trim(),
        durationValue: parseInt(document.getElementById('durationValue').value),
        benefits: document.getElementById('benefits').value.trim(),
    };

    if (!formData.lvName || !formData.lvCode || !formData.price || !formData.durationType || !formData.durationValue || !formData.benefits) {
        showMessage('请填写所有必填字段', 'danger');
        return;
    }

    fetch('/admin/membership/level', {
        method: "POST",
        headers: {
            "Content-type": "application-json"
        },
        body: JSON.stringify(formData)
    })
        .then(response => response.json())
        .then(data => {
            console.log(data)
            loadMembershipLevels();
            resetMembershipForm();
            showMessage('会员月卡添加成功！', 'success');
        })
}

function loadMembershipLevels() {
    fetch('/api/membership/levels')
        .then(response => response.json())
        .then(data => {
            console.log(data)
            if (data.code === 0) {
                memberships = data.data;
                loadMemberships(memberships);
            }
        })
        .catch(error => {
            console.error('Error fetching data:', error);
        });
}

function loadMemberships(filteredData = []) {
    const data = filteredData;
    const tbody = document.getElementById('membershipList');
    tbody.innerHTML = '';
    if (data.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #999;">暂无会员月卡数据</td></tr>';
    } else {
        data.forEach(member => {
            const row = document.createElement('tr');
            row.innerHTML = `
                    <td>${member.lvName}</td>
                    <td>${member.lvCode}</td>
                    <td>${member.price}</td>
                    <td>${member.durationType}</td>
                    <td>${member.durationValue}天</td>
                    <td>${member.benefits}</td>
                    <td>${new Date(member.createdAt).toLocaleString()}</td>
                    <td class="action-buttons">
                        <button class="action-btn edit-btn" onclick="editMembership(${member.oId})">编辑</button>
                        <button class="action-btn delete-btn" onclick="deleteMembership(${member.oId})">删除</button>
                    </td>
                `;
            tbody.appendChild(row);
        });
    }
}

function editMembership(oId) {
    const member = memberships.find(m => m.oId == oId);
    if (member) {
        document.getElementById('lvName').value = member.lvName;
        document.getElementById('lvCode').value = member.lvCode;
        document.getElementById('price').value = member.price;
        document.getElementById('durationType').value = member.durationType;
        document.getElementById('durationValue').value = member.durationValue;
        document.getElementById('benefits').value = member.benefits;

        // 更改按钮为更新模式
        const submitBtn = document.querySelector('#membershipForm button[type="submit"]');
        submitBtn.textContent = '更新会员月卡';
        submitBtn.setAttribute('data-edit-id', oId);
        submitBtn.onclick = function (e) {
            e.preventDefault();
            updateMembership(oId);
        };

        showMessage('请修改会员信息后点击更新', 'success');
    }
}

function updateMembership(oId) {
    const index = memberships.findIndex(m => m.oId == oId);
    if (index !== -1) {
        const formData = {
            lvName: document.getElementById('lvName').value.trim(),
            lvCode: document.getElementById('lvCode').value.trim(),
            price: parseFloat(document.getElementById('price').value),
            durationType: document.getElementById('durationType').value.trim(),
            durationValue: parseInt(document.getElementById('durationValue').value),
            benefits: document.getElementById('benefits').value.trim()
        }
        memberships[index] = {
            ...memberships[index],
            ...formData
        };

        fetch('/admin/membership/level/' + oId, {
            method: "PUT",
            headers: {
                "Content-type": "application-json"
            },
            body: JSON.stringify(formData)
        })
            .then(response => response.json())
            .then(data => {
                console.log(data)
                loadMembershipLevels();
                resetMembershipForm();
                showMessage('会员月卡更新成功！', 'success');
            })
    }
}

function deleteMembership(oId) {
    if (confirm('确定要删除这个会员月卡吗？此操作不可恢复。')) {
        memberships = memberships.filter(m => m.oId != oId);
        fetch('/admin/membership/level/' + oId, {
            method: "DELETE",
            headers: {
                "Content-type": "application-json"
            },
        })
            .then(response => response.json())
            .then(data => {
                console.log(data)
                loadMembershipLevels();
                showMessage('会员月卡删除成功！', 'success');
            })
    }
}

function resetMembershipForm() {
    document.getElementById('membershipForm').reset();
    const submitBtn = document.querySelector('#membershipForm button[type="submit"]');
    submitBtn.textContent = '添加会员月卡';
    submitBtn.removeAttribute('data-edit-id');
    submitBtn.onclick = function (e) {
        e.preventDefault();
        addMembership();
    };
}

// 优惠券相关函数
function addCoupon() {
    const formData = {
        coupon_type: parseInt(document.getElementById('couponType').value.trim()),
        coupon_code: document.getElementById('couponCode').value.trim().toUpperCase(),
        discount: parseFloat(document.getElementById('discount').value),
        times: parseInt(document.getElementById('times').value),
    };

    if (!formData.times || !formData.discount) {
        showMessage('请填写所有必填字段', 'danger');
        return;
    }

    fetch('/admin/coupon', {
        method: "POST",
        headers: {
            "Content-type": "application-json"
        },
        body: JSON.stringify(formData)
    })
        .then(response => response.json())
        .then(data => {
            console.log(data)
            loadCoupons();
            resetCouponForm();
            showMessage('优惠券添加成功！', 'success');
        })
}

function loadCoupons(filteredData = []) {
    fetch('/admin/coupons')
        .then(response => response.json())
        .then(data => {
            console.log(data)
            if (data.code === 0) {
                coupon = data.data;
                const tbody = document.getElementById('couponList');

                tbody.innerHTML = '';

                if (coupon.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #999;">暂无优惠券数据</td></tr>';
                    return;
                }

                coupon.forEach(cp => {
                    const timesText = cp.times === -1 ? '无限制' : cp.times;
                    const row = document.createElement('tr');
                    row.innerHTML = `
                    <td><strong>${cp.coupon_code}</strong></td>
                    <td>${cp.coupon_type}</td>
                    <td>${cp.discount}</td>
                    <td>${timesText}</td>
                    <td>${new Date(cp.createdAt).toLocaleString()}</td>
                    <td class="action-buttons">
                        <button class="action-btn edit-btn" onclick="editCoupon(${cp.oId})">编辑</button>
                        <button class="action-btn delete-btn" onclick="deleteCoupon(${cp.oId})">删除</button>
                    </td>
                `;
                    tbody.appendChild(row);
                });
            }
        })
        .catch(error => {
            console.error('Error fetching data:', error);
        });
}

function editCoupon(oId) {
    const cp = coupon.find(c => c.oId == oId);
    if (cp) {
        document.getElementById('couponType').value = cp.coupon_type;
        document.getElementById('couponCode').value = cp.coupon_code;
        document.getElementById('discount').value = cp.discount;
        document.getElementById('times').value = cp.times;

        // 更改按钮为更新模式
        const submitBtn = document.querySelector('#couponForm button[type="submit"]');
        submitBtn.textContent = '更新优惠券';
        submitBtn.setAttribute('data-edit-id', oId);
        submitBtn.onclick = function (e) {
            e.preventDefault();
            updateCoupon(oId);
        };

        showMessage('请修改优惠券信息后点击更新', 'success');
    }
}

function updateCoupon(oId) {
    const index = coupon.findIndex(c => c.oId == oId);
    const formData = {
        coupon_type: parseInt(document.getElementById('couponType').value.trim()),
        coupon_code: document.getElementById('couponCode').value.trim().toUpperCase(),
        discount: parseFloat(document.getElementById('discount').value),
        times: parseInt(document.getElementById('times').value),
    }
    if (index !== -1) {
        coupon[index] = {
            ...coupon[index],
            ...formData,
        };

        fetch('/admin/coupon/' + oId, {
            method: "PUT",
            headers: {
                "Content-type": "application-json"
            },
            body: JSON.stringify(formData)
        })
            .then(response => response.json())
            .then(data => {
                console.log(data)
                loadCoupons();
                resetCouponForm();
                showMessage('优惠券更新成功！', 'success');
            })
    }
}

function deleteCoupon(oId) {
    if (confirm('确定要删除这个优惠券吗？此操作不可恢复。')) {
        fetch('/admin/coupon/' + oId, {
            method: "DELETE",
            headers: {
                "Content-type": "application-json"
            },
        })
            .then(response => response.json())
            .then(data => {
                console.log(data)
                loadCoupons();
                showMessage('优惠券删除成功！', 'success');
            })
    }
}

function resetCouponForm() {
    document.getElementById('couponForm').reset();

    const submitBtn = document.querySelector('#couponForm button[type="submit"]');
    submitBtn.textContent = '添加优惠券';
    submitBtn.removeAttribute('data-edit-id');
    submitBtn.onclick = function (e) {
        e.preventDefault();
        addCoupon();
    };
}

// 通用函数
function showMessage(text, type) {
    Util.notice(type, 30000, text);
}