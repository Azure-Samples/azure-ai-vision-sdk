export const personIcon = './assets/Person.png';
export const heartPulseIcon = './assets/HeartPulse.png';
export const checkmarkCircleIcon = './assets/CheckmarkCircle.png';
export const dismissCircleIcon = './assets/DismissCircle.png';

export const createOrUpdateFeedbackItem = (id, icon, text) => {
  let itemDiv = document.getElementById(id);
  
  if (!itemDiv) {
    itemDiv = document.createElement('div');
    itemDiv.className = 'item';
    itemDiv.id = id;
    
    const img = document.createElement('img');
    img.id = `${id}-img`;
    itemDiv.appendChild(img);
    
    const span = document.createElement('span');
    span.id = `${id}-text`;
    itemDiv.appendChild(span);
    
    feedbackContainer.appendChild(itemDiv);
  }
  
  document.getElementById(`${id}-img`).src = icon;
  document.getElementById(`${id}-img`).alt = text;
  document.getElementById(`${id}-text`).textContent = text;
};

export const createOrUpdateLine = (id) => {
  let lineDiv = document.getElementById(id);
  
  if (!lineDiv) {
    lineDiv = document.createElement('div');
    lineDiv.className = 'separationLine';
    lineDiv.id = id;
    feedbackContainer.appendChild(lineDiv);
  }
};